package com.eter.eterbackend.activos.infrastructure.config;

import org.slf4j.Logger;
import java.nio.charset.StandardCharsets;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import com.eter.eterbackend.activos.domain.exception.DatoInvalidoException;
import com.eter.eterbackend.activos.infrastructure.adapter.in.kafka.MensajeKafkaInvalidoException;

/**
 * Que hacer cuando el consumidor falla.
 *
 * - Error transitorio (la base de datos no responde): se reintenta el mismo
 *   mensaje con espera creciente (1s, 2s, 4s, 8s, 10s, 10s). El offset no se
 *   confirma mientras tanto, asi que el mensaje no se pierde. Reintentar es
 *   seguro porque el consumidor es idempotente (Redis + UNIQUE nit_univ + idempotency_key).
 *   Tambien se reintenta EventoEnProcesoException (otra instancia tiene el evento).
 * - Mensaje invalido, o reintentos agotados: se publica en el topico
 *   <topico>.DLT para revisarlo despues, y el consumidor sigue con el siguiente.
 *
 * El nombre del DLT se fija a mano: Spring Kafka 4 cambio el sufijo por defecto
 * a "-dlt", y como los topicos NO se crean solos, el envio quedaba bloqueado.
 */
@Configuration
public class KafkaErrorConfig {

	private static final Logger log = LoggerFactory.getLogger(KafkaErrorConfig.class);

	@Bean
	public DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> kafkaTemplate,
			@Value("${spring.kafka.consumer.group-id}") String grupo) {
		var dlt = new DeadLetterPublishingRecoverer(kafkaTemplate,
				(registro, ex) -> new TopicPartition(registro.topic() + ".DLT", registro.partition()));
		// Varios grupos leen el mismo topico: cada uno deja su copia en el DLT y
		// eter-consumer-group dice cual fue; eter-error lleva la causa de fondo.
		// El stacktrace no se copia (ya esta en el log).
		dlt.setHeadersFunction((registro, ex) -> new RecordHeaders(new RecordHeader[] {
				new RecordHeader("eter-consumer-group", grupo.getBytes(StandardCharsets.UTF_8)),
				new RecordHeader("eter-error", causa(ex).getBytes(StandardCharsets.UTF_8)) }));
		dlt.excludeHeader(DeadLetterPublishingRecoverer.HeaderNames.HeadersToAdd.EX_STACKTRACE);

		var espera = new ExponentialBackOffWithMaxRetries(6);
		espera.setInitialInterval(1_000L);
		espera.setMultiplier(2.0);
		espera.setMaxInterval(10_000L);

		var manejador = new DefaultErrorHandler((registro, ex) -> {
			log.error("Mensaje offset {} de {} (particion {}) enviado a {}.DLT: {}",
					registro.offset(), registro.topic(), registro.partition(), registro.topic(), causa(ex));
			dlt.accept(registro, ex);
		}, espera);

		manejador.setRetryListeners((registro, ex, intento) ->
				log.warn("Fallo procesando offset {} de {} (intento {}): {}. Se reintentará.",
						registro.offset(), registro.topic(), intento, ex.getMessage()));

		// Estos errores nunca se arreglan reintentando: van directo al DLT.
		manejador.addNotRetryableExceptions(
				MensajeKafkaInvalidoException.class,
				DatoInvalidoException.class,
				IllegalArgumentException.class,
				NullPointerException.class);
		return manejador;
	}

	/** El listener envuelve el error real; para el log interesa la causa de fondo. */
	private static String causa(Exception ex) {
		Throwable raiz = NestedExceptionUtils.getMostSpecificCause(ex);
		// Solo la primera linea: Jackson agrega "at [Source: ...]" en otra linea.
		String mensaje = String.valueOf(raiz.getMessage()).lines().findFirst().orElse("");
		return raiz.getClass().getSimpleName() + ": " + mensaje;
	}
}
