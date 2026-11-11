package com.eter.eterbackend.shared.activos;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Pregunta a activos-service si un activo existe.
 *
 * Tiene timeouts para no quedarse esperando si activos no responde, y convierte
 * cualquier fallo de comunicacion en ServicioExternoNoDisponibleException (503)
 * en lugar de dejar escapar un 500 generico.
 */
@Component
public class ActivoRestClient implements ActivoExistencePort {

	private static final Logger log = LoggerFactory.getLogger(ActivoRestClient.class);

	private final RestClient client;
	private final String baseUrl;

	public ActivoRestClient(@Value("${eter.services.activos-base-url}") String baseUrl,
			@Value("${eter.services.activos-timeout-ms:3000}") long timeoutMs) {
		SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
		fabrica.setConnectTimeout(Duration.ofMillis(timeoutMs));
		fabrica.setReadTimeout(Duration.ofMillis(timeoutMs));
		this.client = RestClient.builder().requestFactory(fabrica).build();
		this.baseUrl = baseUrl;
	}

	@Override
	public boolean existsById(String nit, String placa) {
		try {
			var spec = client.get().uri(baseUrl + "/api/v1/activos/activos-fijos/{nit}/{placa}", nit, placa);
			String auth = authorization();
			if (auth != null) {
				spec.header(HttpHeaders.AUTHORIZATION, auth);
			}
			spec.retrieve().toBodilessEntity();
			return true;
		} catch (HttpClientErrorException.NotFound e) {
			return false;
		} catch (RestClientException e) {
			// Caido, timeout, 5xx o una respuesta inesperada (401/403): no se puede
			// saber si el activo existe, asi que la operacion no debe continuar.
			log.error("Fallo la consulta a activos-service ({}/{}): {}", nit, placa, e.getMessage());
			throw new ServicioExternoNoDisponibleException(
					"No fue posible verificar el activo porque el servicio de activos no está disponible. "
							+ "Intente de nuevo en unos minutos.");
		}
	}

	private String authorization() {
		var a = RequestContextHolder.getRequestAttributes();
		if (a instanceof ServletRequestAttributes s) {
			return s.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
		}
		return null;
	}
}
