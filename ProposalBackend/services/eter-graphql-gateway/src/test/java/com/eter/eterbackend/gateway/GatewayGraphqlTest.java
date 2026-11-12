package com.eter.eterbackend.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import tools.jackson.databind.json.JsonMapper;

/**
 * Gateway real (puerto aleatorio) contra microservicios simulados con el servidor HTTP del JDK.
 * Cubre el contrato del que depende el frontend: códigos de error, composición sin N+1,
 * resumen, CORS y suscripción por WebSocket.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayGraphqlTest {

	private static final String SECRETO = "eter-jwt-secret-development-only-CHANGE-IN-PRODUCTION-9f4a8c7e";
	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final HttpClient HTTP = HttpClient.newHttpClient();

	private static final List<String> LLAMADAS = new CopyOnWriteArrayList<>();
	private static volatile List<Map<String, Object>> notificaciones = List.of();
	private static final HttpServer MICROSERVICIOS = iniciarMicroservicios();

	@Value("${local.server.port}")
	private int puerto;

	private final String token = token(SECRETO);

	@DynamicPropertySource
	static void urlsDeMicroservicios(DynamicPropertyRegistry registro) {
		String base = "http://127.0.0.1:" + MICROSERVICIOS.getAddress().getPort();
		for (String servicio : List.of("activos", "prestamos", "inventario", "notificaciones")) {
			registro.add("eter.services." + servicio + "-base-url", () -> base);
		}
		registro.add("eter.gateway.poll-notificaciones-ms", () -> "100");
	}

	@AfterAll
	static void apagar() {
		MICROSERVICIOS.stop(0);
	}

	@BeforeEach
	void limpiar() {
		LLAMADAS.clear();
		notificaciones = List.of();
	}

	@Test
	@DisplayName("Sin token no hay datos: UNAUTHENTICATED")
	void sinToken() throws Exception {
		Map<String, Object> r = graphql("{ me { email } }", null);
		assertEquals("UNAUTHENTICATED", codigoError(r));
	}

	@Test
	@DisplayName("Token con firma falsa: UNAUTHENTICATED")
	void tokenConFirmaFalsa() throws Exception {
		Map<String, Object> r = graphql("{ me { email } }", "Bearer " + token("otro-secreto-de-al-menos-32-bytes-de-largo!!"));
		assertEquals("UNAUTHENTICATED", codigoError(r));
	}

	@Test
	@DisplayName("me sale del token")
	void meSaleDelToken() throws Exception {
		Map<String, Object> r = graphql("{ me { email nitUniv roles } }", "Bearer " + token);
		assertEquals(Map.of("email", "a@b.co", "nitUniv", "900123456-1", "roles", List.of("ADMIN")), datos(r).get("me"));
	}

	@Test
	@DisplayName("Solo se consultan los servicios de los campos pedidos")
	void soloLosCamposPedidos() throws Exception {
		Map<String, Object> r = graphql("{ activos(estado: ACTIVO) { placaInventario } }", "Bearer " + token);
		assertEquals(List.of(Map.of("placaInventario", "AF-1")), datos(r).get("activos"));
		assertEquals(1, LLAMADAS.size(), LLAMADAS.toString());
	}

	@Test
	@DisplayName("Composición préstamo + activo sin N+1")
	void composicionSinNMasUno() throws Exception {
		Map<String, Object> r = graphql("{ prestamos(soloVencidos: true) { idPrestamo activo { descripcion } } }", "Bearer " + token);
		List<?> prestamos = (List<?>) datos(r).get("prestamos");
		assertEquals("Portatil", ((Map<?, ?>) ((Map<?, ?>) prestamos.get(0)).get("activo")).get("descripcion"));
		assertEquals(2, LLAMADAS.size(), LLAMADAS.toString());
	}

	@Test
	@DisplayName("5 préstamos de 3 activos: 3 llamadas a activos aunque los campos se resuelvan en paralelo")
	void sinNMasUnoConCamposEnParalelo() throws Exception {
		Map<String, Object> r = graphql("{ prestamos(estado: DEVUELTO) { idPrestamo activo { placaInventario } } }", "Bearer " + token);
		List<?> prestamos = (List<?>) datos(r).get("prestamos");
		assertEquals(5, prestamos.size());
		assertEquals("AF-3", ((Map<?, ?>) ((Map<?, ?>) prestamos.get(4)).get("activo")).get("placaInventario"));
		long llamadasAActivos = LLAMADAS.stream().filter(l -> l.startsWith("GET /api/v1/activos/activos-fijos/")).count();
		assertEquals(3, llamadasAActivos, LLAMADAS.toString());
	}

	@Test
	@DisplayName("El resumen agrega activos, préstamos y notificaciones")
	void resumenAgregaVariosServicios() throws Exception {
		Map<String, Object> r = graphql(
				"{ resumen { totalActivos prestamosVigentes prestamosVencidos notificacionesNoLeidas activosPorEstado { estado total } } }",
				"Bearer " + token);
		Map<?, ?> resumen = (Map<?, ?>) datos(r).get("resumen");
		assertEquals(2, resumen.get("totalActivos"));
		assertEquals(1, resumen.get("prestamosVigentes"));
		assertEquals(1, resumen.get("prestamosVencidos"));
		assertEquals(0, resumen.get("notificacionesNoLeidas"));
		assertEquals(List.of(Map.of("estado", "ACTIVO", "total", 1), Map.of("estado", "BAJA", "total", 1)),
				resumen.get("activosPorEstado"));
	}

	@Test
	@DisplayName("404 del microservicio: activo devuelve null, sin error")
	void activoInexistenteEsNull() throws Exception {
		Map<String, Object> r = graphql("{ activo(placaInventario: \"NOPE\") { placaInventario } }", "Bearer " + token);
		assertNull(datos(r).get("activo"));
		assertNull(r.get("errors"));
	}

	@Test
	@DisplayName("409 del microservicio: CONFLICT con el mensaje y el servicio")
	void conflictoLlegaConSuCodigo() throws Exception {
		String mutation = """
				mutation { crearPrestamo(input: { placaInventario: "AF-OCUPADO", numDocumento: "79123456",
				  fechaSalida: "2026-05-01T08:00:00", fechaDevolucionPactada: "2026-05-08T08:00:00" }) { idPrestamo } }""";
		Map<String, Object> r = graphql(mutation, "Bearer " + token);
		Map<?, ?> error = (Map<?, ?>) ((List<?>) r.get("errors")).get(0);
		Map<?, ?> extensiones = (Map<?, ?>) error.get("extensions");
		assertEquals("CONFLICT", extensiones.get("code"));
		assertEquals("prestamos", extensiones.get("servicio"));
		assertEquals("El activo AF-OCUPADO ya tiene un préstamo vigente.", error.get("message"));
	}

	@Test
	@DisplayName("DateTime inválido se rechaza antes de llamar al microservicio")
	void fechaInvalida() throws Exception {
		Map<String, Object> r = graphql(
				"mutation { registrarDevolucion(idPrestamo: \"p1\", fechaDevolucionReal: \"ayer\") { idPrestamo } }",
				"Bearer " + token);
		assertNotNull(r.get("errors"));
		assertEquals(0, LLAMADAS.size());
	}

	@Test
	@DisplayName("CORS: acepta la página abierta desde file:// (origen null)")
	void corsParaOrigenNull() throws Exception {
		HttpRequest preflight = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + "/graphql"))
				.method("OPTIONS", HttpRequest.BodyPublishers.noBody())
				.header("Origin", "null")
				.header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "authorization,content-type")
				.build();
		HttpResponse<String> r = HTTP.send(preflight, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, r.statusCode());
		assertEquals("*", r.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
	}

	@Test
	@DisplayName("Suscripción por WebSocket: emite solo las notificaciones nuevas")
	void suscripcionEmiteSoloLasNuevas() throws Exception {
		BlockingQueue<Map<String, Object>> mensajes = new LinkedBlockingQueue<>();
		WebSocket ws = HTTP.newWebSocketBuilder()
				.subprotocols("graphql-transport-ws")
				.buildAsync(URI.create("ws://127.0.0.1:" + puerto + "/graphql"), new Oyente(mensajes))
				.get(5, TimeUnit.SECONDS);

		enviar(ws, Map.of("type", "connection_init", "payload", Map.of("Authorization", "Bearer " + token)));
		assertEquals("connection_ack", siguiente(mensajes, "connection_ack").get("type"));
		enviar(ws, Map.of("id", "1", "type", "subscribe",
				"payload", Map.of("query", "subscription { notificacionRecibida { idNotificacion titulo } }")));

		// La primera consulta fija la línea base; esta notificación llega después.
		Thread.sleep(350);
		notificaciones = List.of(Map.of("idNotificacion", "n1", "titulo", "Vencido", "mensaje", "m",
				"tipo", "PRESTAMO_VENCIDO", "leida", false, "creadaEn", "2026-01-01T00:00:00"));

		Map<String, Object> next = siguiente(mensajes, "next");
		Map<?, ?> data = (Map<?, ?>) ((Map<?, ?>) next.get("payload")).get("data");
		assertEquals(Map.of("idNotificacion", "n1", "titulo", "Vencido"), data.get("notificacionRecibida"));
		ws.sendClose(WebSocket.NORMAL_CLOSURE, "fin").get(5, TimeUnit.SECONDS);
	}

	// Utilidades

	private Map<String, Object> graphql(String query, String autorizacion) throws Exception {
		HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + "/graphql"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(Map.of("query", query))));
		if (autorizacion != null) {
			peticion.header("Authorization", autorizacion);
		}
		HttpResponse<byte[]> r = HTTP.send(peticion.build(), HttpResponse.BodyHandlers.ofByteArray());
		return mapa(JSON.readValue(r.body(), Map.class));
	}

	private static Map<String, Object> datos(Map<String, Object> respuesta) {
		assertNull(respuesta.get("errors"), () -> "errores inesperados: " + respuesta.get("errors"));
		return mapa(respuesta.get("data"));
	}

	private static String codigoError(Map<String, Object> respuesta) {
		Map<?, ?> error = (Map<?, ?>) ((List<?>) respuesta.get("errors")).get(0);
		return String.valueOf(((Map<?, ?>) error.get("extensions")).get("code"));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> mapa(Object valor) {
		return (Map<String, Object>) valor;
	}

	private static String token(String secreto) {
		SecretKey clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
		return Jwts.builder().subject("a@b.co").claim("nitUniv", "900123456-1").claim("roles", List.of("ADMIN"))
				.signWith(clave).compact();
	}

	private static void enviar(WebSocket ws, Map<String, Object> mensaje) throws Exception {
		ws.sendText(JSON.writeValueAsString(mensaje), true).get(5, TimeUnit.SECONDS);
	}

	/** Espera el siguiente mensaje del tipo indicado (ignora los ping del keep-alive). */
	private static Map<String, Object> siguiente(BlockingQueue<Map<String, Object>> mensajes, String tipo) throws Exception {
		long limite = System.currentTimeMillis() + 5000;
		while (System.currentTimeMillis() < limite) {
			Map<String, Object> m = mensajes.poll(limite - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
			if (m != null && tipo.equals(m.get("type"))) {
				return m;
			}
		}
		throw new AssertionError("No llegó un mensaje '" + tipo + "' en 5 s");
	}

	/** Junta los fragmentos de texto del WebSocket y los entrega como JSON. */
	private record Oyente(BlockingQueue<Map<String, Object>> mensajes, StringBuilder parcial) implements WebSocket.Listener {
		Oyente(BlockingQueue<Map<String, Object>> mensajes) {
			this(mensajes, new StringBuilder());
		}

		@Override
		public CompletionStage<?> onText(WebSocket ws, CharSequence fragmento, boolean ultimo) {
			parcial.append(fragmento);
			if (ultimo) {
				mensajes.add(mapa(JSON.readValue(parcial.toString(), Map.class)));
				parcial.setLength(0);
			}
			ws.request(1);
			return null;
		}
	}

	// Microservicios simulados

	private static HttpServer iniciarMicroservicios() {
		try {
			HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			servidor.createContext("/", GatewayGraphqlTest::responder);
			// Atiende en paralelo, como un microservicio real.
			servidor.setExecutor(Executors.newCachedThreadPool());
			servidor.start();
			return servidor;
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static void responder(HttpExchange intercambio) throws IOException {
		String metodo = intercambio.getRequestMethod();
		String ruta = intercambio.getRequestURI().getPath();
		String consulta = intercambio.getRequestURI().getRawQuery();
		LLAMADAS.add(metodo + " " + ruta + (consulta != null ? "?" + consulta : ""));

		Map<String, Object> activo = Map.of("nitUniv", "900123456-1", "placaInventario", "AF-1", "descripcion", "Portatil",
				"codUnspsc", "43211503", "codSede", "SED-01", "valorContable", 100.5, "estado", "ACTIVO");

		if (ruta.equals("/api/v1/activos/activos-fijos") && metodo.equals("GET")) {
			Map<String, Object> deBaja = new java.util.HashMap<>(activo);
			deBaja.put("placaInventario", "AF-2");
			deBaja.put("estado", "BAJA");
			json(intercambio, 200, List.of(activo, deBaja));
		} else if (ruta.equals("/api/v1/activos/activos-fijos/900123456-1/NOPE")) {
			json(intercambio, 404, Map.of("title", "No encontrado", "detail", "Activo NOPE no existe"));
		} else if (ruta.startsWith("/api/v1/activos/activos-fijos/900123456-1/")) {
			// Responde con demora para que las consultas simultáneas se crucen.
			pausa(150);
			Map<String, Object> pedido = new java.util.HashMap<>(activo);
			pedido.put("placaInventario", ruta.substring(ruta.lastIndexOf('/') + 1));
			json(intercambio, 200, pedido);
		} else if (ruta.equals("/api/v1/activos/prestamos") && metodo.equals("POST")) {
			json(intercambio, 409, Map.of("title", "Conflict", "detail", "El activo AF-OCUPADO ya tiene un préstamo vigente."));
		} else if (ruta.equals("/api/v1/activos/prestamos") && consulta != null && consulta.contains("estado=DEVUELTO")) {
			// 5 préstamos de 3 activos distintos (AF-1 y AF-3 se repiten).
			json(intercambio, 200, List.of("AF-1", "AF-3", "AF-1", "AF-4", "AF-3").stream()
					.map(placa -> Map.<String, Object>of("idPrestamo", "d-" + placa + "-" + Math.random(), "nitUniv", "900123456-1",
							"placaInventario", placa, "numDocumento", "CC12345", "fechaSalida", "2026-01-01T00:00:00",
							"fechaDevolucionPactada", "2026-01-02T00:00:00", "estado", "DEVUELTO", "vencido", false))
					.toList());
		} else if (ruta.equals("/api/v1/activos/prestamos")) {
			json(intercambio, 200, List.of(Map.of("idPrestamo", "p1", "nitUniv", "900123456-1", "placaInventario", "AF-1",
					"numDocumento", "CC12345", "fechaSalida", "2026-01-01T00:00:00", "fechaDevolucionPactada", "2026-01-02T00:00:00",
					"estado", "VIGENTE", "vencido", true)));
		} else if (ruta.equals("/api/v1/notificaciones")) {
			List<Map<String, Object>> items = notificaciones;
			json(intercambio, 200, Map.of("items", items, "page", 0, "size", 1, "totalElements", items.size(), "noLeidas", items.size()));
		} else {
			json(intercambio, 500, Map.of("title", "inesperado"));
		}
	}

	private static void pausa(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private static void json(HttpExchange intercambio, int status, Object cuerpo) throws IOException {
		byte[] bytes = JSON.writeValueAsBytes(cuerpo);
		intercambio.getResponseHeaders().add("Content-Type", "application/json");
		intercambio.sendResponseHeaders(status, bytes.length);
		try (OutputStream salida = intercambio.getResponseBody()) {
			salida.write(bytes);
		}
	}
}
