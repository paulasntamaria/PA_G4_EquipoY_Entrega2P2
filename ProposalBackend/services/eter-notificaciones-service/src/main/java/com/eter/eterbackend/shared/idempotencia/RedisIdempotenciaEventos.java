package com.eter.eterbackend.shared.idempotencia;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Idempotencia en Redis; si Redis falla, el UNIQUE de la base frena duplicados. */
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
			// La clave expiro: se intenta de nuevo.
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
			// Ya quedo en la base; el UNIQUE frena duplicados.
			log.warn("No se pudo marcar {} como PROCESADO en Redis: {}", clave, ex.getMessage());
		}
	}

	private void liberar(String clave) {
		try {
			redis.delete(clave);
		} catch (DataAccessException ex) {
			// PROCESANDO expira sola.
			log.warn("No se pudo liberar {} en Redis: {}", clave, ex.getMessage());
		}
	}
}
