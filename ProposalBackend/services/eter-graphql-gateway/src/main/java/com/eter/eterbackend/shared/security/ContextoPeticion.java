package com.eter.eterbackend.shared.security;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.eter.eterbackend.shared.error.GatewayException;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;

/**
 * Lo que {@link AutenticacionInterceptor} deja en el GraphQLContext de cada operación:
 * el usuario del JWT (si es válido) y una caché que vive lo que dura la petición.
 */
public final class ContextoPeticion {

	static final String USUARIO = "eter.usuario";
	static final String CACHE = "eter.cache";

	private final GraphQLContext contexto;

	private ContextoPeticion(GraphQLContext contexto) {
		this.contexto = contexto;
	}

	public static ContextoPeticion de(DataFetchingEnvironment env) {
		return new ContextoPeticion(env.getGraphQlContext());
	}

	static Map<String, Object> valoresIniciales(UsuarioAutenticado usuario) {
		Map<String, Object> valores = new HashMap<>();
		valores.put(CACHE, new ConcurrentHashMap<String, Object>());
		if (usuario != null) {
			valores.put(USUARIO, usuario);
		}
		return valores;
	}

	/** Usuario del token; sin token válido no se entrega ningún dato. */
	public UsuarioAutenticado usuario() {
		UsuarioAutenticado usuario = contexto.get(USUARIO);
		if (usuario == null) {
			throw GatewayException.noAutenticado();
		}
		return usuario;
	}

	public String token() {
		return usuario().token();
	}

	/** El nitUniv nunca lo manda el cliente: sale del token. */
	public String nit() {
		return usuario().nitUniv();
	}

	/**
	 * Memoiza una consulta por petición para evitar N+1: si una lista de 20 préstamos pide
	 * el mismo activo, se consulta una vez.
	 * <p>
	 * Spring for GraphQL resuelve los campos en paralelo (hilos virtuales), así que se guarda la
	 * consulta <em>en curso</em>: quien llega mientras otro hilo consulta la misma clave espera
	 * ese resultado en vez de repetir la llamada. Si la consulta falla, se quita de la caché.
	 */
	@SuppressWarnings("unchecked")
	public <T> T memo(String clave, Supplier<T> consulta) {
		Map<String, Object> cache = contexto.get(CACHE);
		if (cache == null) {
			return consulta.get();
		}
		CompletableFuture<Object> propia = new CompletableFuture<>();
		Object enCurso = cache.putIfAbsent(clave, propia);
		if (enCurso == null) {
			try {
				T valor = consulta.get();
				propia.complete(valor);
				return valor;
			} catch (RuntimeException ex) {
				cache.remove(clave, propia);
				propia.completeExceptionally(ex);
				throw ex;
			}
		}
		try {
			return (T) ((CompletableFuture<Object>) enCurso).join();
		} catch (CompletionException ex) {
			// Mismo error que recibió el hilo que hizo la consulta (ej. NOT_FOUND).
			if (ex.getCause() instanceof RuntimeException causa) {
				throw causa;
			}
			throw ex;
		}
	}

	/** Tras una mutación, descarta lo memoizado que empiece por el prefijo. */
	public void invalidar(String prefijo) {
		Map<String, Object> cache = contexto.get(CACHE);
		if (cache != null) {
			cache.keySet().removeIf(clave -> clave.startsWith(prefijo));
		}
	}
}
