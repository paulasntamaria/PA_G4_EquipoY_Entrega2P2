package com.eter.eterbackend.shared.idempotencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@DisplayName("RedisIdempotenciaEventos (idempotencia del consumidor)")
class RedisIdempotenciaEventosTest {

	private static final String EVENT_ID = "6f1c2c1e-9a8b-4c7d-8e6f-5a4b3c2d1e0f";
	private static final String CLAVE = "eter:idem:grupo-prueba:" + EVENT_ID;

	private StringRedisTemplate redis;
	private ValueOperations<String, String> valores;
	private RedisIdempotenciaEventos idempotencia;
	private AtomicInteger ejecuciones;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void preparar() {
		redis = mock(StringRedisTemplate.class);
		valores = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(valores);
		idempotencia = new RedisIdempotenciaEventos(redis, "grupo-prueba",
				Duration.ofMinutes(5), Duration.ofDays(7));
		ejecuciones = new AtomicInteger();
	}

	@Test
	@DisplayName("primera entrega: ejecuta la accion y marca la clave como PROCESADO")
	void primeraEntrega() {
		when(valores.setIfAbsent(eq(CLAVE), eq("PROCESANDO"), any(Duration.class))).thenReturn(true);

		var resultado = idempotencia.ejecutarUnaVez(EVENT_ID, ejecuciones::incrementAndGet);

		assertEquals(IdempotenciaEventos.Resultado.PROCESADO, resultado);
		assertEquals(1, ejecuciones.get());
		verify(valores).set(CLAVE, "PROCESADO", Duration.ofDays(7));
	}

	@Test
	@DisplayName("evento duplicado: NO ejecuta la accion")
	void duplicado() {
		when(valores.setIfAbsent(eq(CLAVE), eq("PROCESANDO"), any(Duration.class))).thenReturn(false);
		when(valores.get(CLAVE)).thenReturn("PROCESADO");

		var resultado = idempotencia.ejecutarUnaVez(EVENT_ID, ejecuciones::incrementAndGet);

		assertEquals(IdempotenciaEventos.Resultado.DUPLICADO, resultado);
		assertEquals(0, ejecuciones.get());
	}

	@Test
	@DisplayName("otra instancia lo esta procesando: lanza excepcion reintentable")
	void enProceso() {
		when(valores.setIfAbsent(eq(CLAVE), eq("PROCESANDO"), any(Duration.class))).thenReturn(false);
		when(valores.get(CLAVE)).thenReturn("PROCESANDO");

		assertThrows(EventoEnProcesoException.class,
				() -> idempotencia.ejecutarUnaVez(EVENT_ID, ejecuciones::incrementAndGet));
		assertEquals(0, ejecuciones.get());
	}

	@Test
	@DisplayName("si la accion falla se libera la clave para que el reintento pueda entrar")
	void accionFalla() {
		when(valores.setIfAbsent(eq(CLAVE), eq("PROCESANDO"), any(Duration.class))).thenReturn(true);

		assertThrows(IllegalStateException.class, () -> idempotencia.ejecutarUnaVez(EVENT_ID, () -> {
			throw new IllegalStateException("base de datos caida");
		}));

		verify(redis).delete(CLAVE);
		verify(valores, never()).set(anyString(), eq("PROCESADO"), any(Duration.class));
	}

	@Test
	@DisplayName("Redis caido: procesa igual (fail-open) y la base frena duplicados")
	void redisCaido() {
		when(valores.setIfAbsent(anyString(), anyString(), any(Duration.class)))
				.thenThrow(new RedisConnectionFailureException("Connection refused"));

		var resultado = idempotencia.ejecutarUnaVez(EVENT_ID, ejecuciones::incrementAndGet);

		assertEquals(IdempotenciaEventos.Resultado.PROCESADO_SIN_REDIS, resultado);
		assertEquals(1, ejecuciones.get());
	}
}
