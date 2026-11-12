package com.eter.eterbackend.gateway.infrastructure.adapter.out.rest;

import java.util.List;
import java.util.Map;

/** Conversión del JSON genérico (mapas y listas) que devuelven los microservicios. */
public final class Json {

	private Json() {
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> objeto(Object valor) {
		return valor instanceof Map<?, ?> mapa ? (Map<String, Object>) mapa : null;
	}

	@SuppressWarnings("unchecked")
	public static List<Map<String, Object>> lista(Object valor) {
		return valor instanceof List<?> lista ? (List<Map<String, Object>>) lista : List.of();
	}

	public static int entero(Map<String, Object> objeto, String campo) {
		return objeto != null && objeto.get(campo) instanceof Number numero ? numero.intValue() : 0;
	}
}
