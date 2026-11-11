package com.eter.eterbackend.notificaciones.infrastructure.config;

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

import com.eter.eterbackend.notificaciones.infrastructure.adapter.in.kafka.MensajeKafkaInvalidoException;

/** Errores transitorios: reintento con espera creciente. Invalidos o agotados: <topico>.DLT. */
@Configuration
public class KafkaErrorConfig {

	private static final Logger log = LoggerFactory.getLogger(KafkaErrorConfig.class);

	@Bean
	public DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> kafkaTemplate,
			@Value("${spring.kafka.consumer.group-id}") String grupo) {
		var dlt = new DeadLetterPublishingRecoverer(kafkaTemplate,
				(registro, ex) -> new TopicPartition(registro.topic() + ".DLT", registro.partition()));
		// Cabeceras del DLT: grupo que fallo y causa de fondo.
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

		// Sin reintento: directo al DLT.
		manejador.addNotRetryableExceptions(
				MensajeKafkaInvalidoException.class,
				IllegalArgumentException.class,
				NullPointerException.class);
		return manejador;
	}

	/** Causa de fondo del error. */
	private static String causa(Exception ex) {
		Throwable raiz = NestedExceptionUtils.getMostSpecificCause(ex);
		// Solo la primera linea del mensaje de Jackson.
		String mensaje = String.valueOf(raiz.getMessage()).lines().findFirst().orElse("");
		return raiz.getClass().getSimpleName() + ": " + mensaje;
	}
}
