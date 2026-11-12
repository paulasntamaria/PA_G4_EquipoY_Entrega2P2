package com.eter.eterbackend.shared.security;

import java.util.Map;

import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.server.WebSocketGraphQlInterceptor;
import org.springframework.graphql.server.WebSocketGraphQlRequest;
import org.springframework.graphql.server.WebSocketSessionInfo;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

/**
 * Lee el JWT de cada operación y lo deja en el GraphQLContext.
 * HTTP: cabecera Authorization. WebSocket: el payload de connection_init
 * ({"Authorization": "Bearer ..."}), que se guarda en la sesión al conectar.
 */
@Component
public class AutenticacionInterceptor implements WebSocketGraphQlInterceptor {

	private static final String ATRIBUTO_AUTORIZACION = "eter.authorization";

	private final JwtVerificador verificador;

	public AutenticacionInterceptor(JwtVerificador verificador) {
		this.verificador = verificador;
	}

	@Override
	public Mono<Object> handleConnectionInitialization(WebSocketSessionInfo sesion, Map<String, Object> payload) {
		Object autorizacion = payload.containsKey("Authorization") ? payload.get("Authorization") : payload.get("authorization");
		if (autorizacion instanceof String valor) {
			sesion.getAttributes().put(ATRIBUTO_AUTORIZACION, valor);
		}
		return Mono.empty();
	}

	@Override
	public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
		String cabecera = (request instanceof WebSocketGraphQlRequest ws)
				? (String) ws.getSessionInfo().getAttributes().get(ATRIBUTO_AUTORIZACION)
				: request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

		// Token inválido no corta aquí: cada campo responde UNAUTHENTICATED (igual que antes).
		UsuarioAutenticado usuario = verificador.autenticar(cabecera);
		Map<String, Object> valores = ContextoPeticion.valoresIniciales(usuario);
		request.configureExecutionInput((entrada, builder) -> builder.graphQLContext(valores).build());
		return chain.next(request);
	}
}
