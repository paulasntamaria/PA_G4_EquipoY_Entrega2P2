package com.eter.eterbackend.gateway.infrastructure.adapter.out.rest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import com.eter.eterbackend.shared.error.GatewayException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Llamada REST a un microservicio reenviando el JWT del usuario. Devuelve el JSON como
 * mapas y listas: GraphQL lee los campos por nombre, así que no hacen falta DTO.
 */
@Component
public class MicroservicioRestClient {

	private static final Logger log = LoggerFactory.getLogger(MicroservicioRestClient.class);
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final RestClient client;
	private final Map<Microservicio, String> urlsBase = new EnumMap<>(Microservicio.class);

	public MicroservicioRestClient(
			@Value("${eter.services.activos-base-url}") String activos,
			@Value("${eter.services.prestamos-base-url}") String prestamos,
			@Value("${eter.services.inventario-base-url}") String inventario,
			@Value("${eter.services.notificaciones-base-url}") String notificaciones,
			@Value("${eter.gateway.timeout-ms:5000}") long timeoutMs) {
		urlsBase.put(Microservicio.ACTIVOS, activos);
		urlsBase.put(Microservicio.PRESTAMOS, prestamos);
		urlsBase.put(Microservicio.INVENTARIO, inventario);
		urlsBase.put(Microservicio.NOTIFICACIONES, notificaciones);

		// Cliente HTTP del JDK: soporta PATCH (HttpURLConnection no) y tiempos de espera.
		HttpClient http = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofMillis(timeoutMs))
				.build();
		JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
		fabrica.setReadTimeout(Duration.ofMillis(timeoutMs));
		this.client = RestClient.builder().requestFactory(fabrica).build();
	}

	/** Arma una ruta codificando cada segmento: ruta("/api/v1/x", nit, placa) -> /api/v1/x/{nit}/{placa}. */
	public static String ruta(String base, Object... segmentos) {
		StringBuilder ruta = new StringBuilder(base);
		for (Object segmento : segmentos) {
			ruta.append('/').append(UriUtils.encodePathSegment(String.valueOf(segmento), StandardCharsets.UTF_8));
		}
		return ruta.toString();
	}

	public Object get(Microservicio servicio, String token, String ruta, Map<String, ?> query) {
		return ejecutar(servicio, HttpMethod.GET, token, uri(servicio, ruta, query), null, Map.of());
	}

	public Object enviar(Microservicio servicio, HttpMethod metodo, String token, String ruta, Object cuerpo,
			Map<String, String> cabeceras) {
		return ejecutar(servicio, metodo, token, uri(servicio, ruta, Map.of()), cuerpo, cabeceras);
	}

	private URI uri(Microservicio servicio, String ruta, Map<String, ?> query) {
		UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(urlsBase.get(servicio)).path(ruta);
		query.forEach((clave, valor) -> {
			if (valor != null) {
				String codificado = UriUtils.encodeQueryParam(String.valueOf(valor), StandardCharsets.UTF_8).replace("+", "%2B");
				uri.queryParam(clave, codificado);
			}
		});
		return uri.build(true).toUri();
	}

	private Object ejecutar(Microservicio servicio, HttpMethod metodo, String token, URI uri, Object cuerpo,
			Map<String, String> cabeceras) {
		RestClient.RequestBodySpec peticion = client.method(metodo).uri(uri).accept(MediaType.APPLICATION_JSON);
		if (token != null) {
			peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}
		cabeceras.forEach(peticion::header);
		if (cuerpo != null) {
			peticion.contentType(MediaType.APPLICATION_JSON).body(JSON.writeValueAsBytes(cuerpo));
		}
		try {
			return peticion.exchange((solicitud, respuesta) -> leerRespuesta(servicio, respuesta));
		} catch (RestClientException ex) {
			log.warn("{} {} -> sin respuesta de {}: {}", metodo, uri, servicio.nombre(), ex.getMessage());
			throw GatewayException.noDisponible(servicio.nombre());
		}
	}

	private Object leerRespuesta(Microservicio servicio, ClientHttpResponse respuesta) throws IOException {
		int status = respuesta.getStatusCode().value();
		if (status == 204) {
			return null;
		}
		byte[] cuerpo = respuesta.getBody().readAllBytes();
		Object datos = leerJson(cuerpo);
		if (status >= 400) {
			throw GatewayException.desdeRespuesta(servicio.nombre(), status, datos);
		}
		return datos;
	}

	private static Object leerJson(byte[] cuerpo) {
		if (cuerpo.length == 0) {
			return null;
		}
		try {
			return JSON.readValue(cuerpo, Object.class);
		} catch (JacksonException ex) {
			return null;
		}
	}
}
