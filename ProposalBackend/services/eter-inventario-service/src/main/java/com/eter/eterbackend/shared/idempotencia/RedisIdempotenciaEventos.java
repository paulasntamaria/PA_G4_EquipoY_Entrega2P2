package com.eter.eterbackend.shared.idempotencia;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Idempotencia de consumidores apoyada en Redis.
 *
 * Por cada evento se guarda la clave  eter:idem:{grupo}:{eventId}  con dos estados:
 *
 *  1. SET clave PROCESANDO NX EX (ttl corto)  -> solo una instancia "gana" el evento.
 *  2. Si la accion termina bien: SET clave PROCESADO EX (ttl largo).
 *     Si la accion falla: DEL clave, para que el reintento si pueda entrar.
 *
 * La clave lleva el grupo de consumidor porque un mismo evento lo deben procesar
 * notificaciones E inventario: cada grupo lleva su propia cuenta.
 *
 * Redis es la primera barrera (rapida y sin tocar la base). La segunda son las
 * restricciones UNIQUE de PostgreSQL, que siguen ahi por si Redis se reinicia,
 * la clave expira o Redis no esta disponible (en ese caso se sigue procesando:
 * fail-open, y se deja un WARN en el log).
 */
@Component
public class RedisIdempotenciaEventos implements IdempotenciaEventos {

	private static final Logger log = LoggerFactory.getLogger(RedisIdempotenciaEventos.class);

	static final String PROCESANDO = "PROCESANDO";
	static final String PROCESADO = "PROCESADO";

	private final StringRedisTemplate redis;
	private final String prefijo;
	private final Duration ttlProcesando;
	private final Duration ttlProcesado;

	public RedisIdempotenciaEventos(StringRedisTemplate redis,
			@Value("${spring.kafka.consumer.group-id}") String grupoConsumidor,
			@Value("${eter.idempotencia.ttl-procesando:PT5M}") Duration ttlProcesando,
			@Value("${eter.idempotencia.ttl-procesado:P7D}") Duration ttlProcesado) {
		this.redis = redis;
		this.prefijo = "eter:idem:" + grupoConsumidor + ":";
		this.ttlProcesando = ttlProcesando;
		this.ttlProcesado = ttlProcesado;
	}

	@Override
	public Resultado ejecutarUnaVez(String eventId, Runnable accion) {
		String clave = prefijo + eventId;

		Boolean tomado;
		try {
			tomado = redis.opsForValue().setIfAbsent(clave, PROCESANDO, ttlProcesando);
		} catch (DataAccessException ex) {
			log.warn("Redis no disponible ({}). Evento {} se procesa sin filtro Redis; "
					+ "la restricción UNIQUE de la base evita duplicados.", ex.getMessage(), eventId);
			accion.run();
			return Resultado.PROCESADO_SIN_REDIS;
		}

		if (!Boolean.TRUE.equals(tomado)) {
			String estado = leer(clave);
			if (PROCESADO.equals(estado)) {
				log.info("Evento {} DUPLICADO: ya figura como procesado en Redis ({}). Se ignora.", eventId, clave);
				return Resultado.DUPLICADO;
			}
			if (estado != null) {
				throw new EventoEnProcesoException(
						"El evento %s lo está procesando otra instancia.".formatted(eventId));
			}
			// La clave expiro entre el SET NX y el GET: se vuelve a intentar desde cero.
			return ejecutarUnaVez(eventId, accion);
		}

		try {
			accion.run();
		} catch (RuntimeException ex) {
			liberar(clave);
			throw ex;
		}

		marcarProcesado(clave);
		log.debug("Evento {} marcado como PROCESADO en Redis ({}).", eventId, clave);
		return Resultado.PROCESADO;
	}

	private String leer(String clave) {
		try {
			return redis.opsForValue().get(clave);
		} catch (DataAccessException ex) {
			throw new EventoEnProcesoException("No se pudo leer el estado en Redis: " + ex.getMessage());
		}
	}

	private void marcarProcesado(String clave) {
		try {
			redis.opsForValue().set(clave, PROCESADO, ttlProcesado);
		} catch (DataAccessException ex) {
			// El trabajo ya quedo hecho en la base; si llega un duplicado lo frena el UNIQUE.
			log.warn("No se pudo marcar {} como PROCESADO en Redis: {}", clave, ex.getMessage());
		}
	}

	private void liberar(String clave) {
		try {
			redis.delete(clave);
		} catch (DataAccessException ex) {
			// La clave PROCESANDO expira sola con su ttl corto.
			log.warn("No se pudo liberar {} en Redis: {}", clave, ex.getMessage());
		}
	}
}
