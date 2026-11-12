package com.eter.eterbackend.gateway.infrastructure.adapter.out.rest;

/** Microservicios detrás del gateway. El nombre es el que ve el cliente en extensions.servicio. */
public enum Microservicio {
	ACTIVOS("activos"),
	PRESTAMOS("prestamos"),
	INVENTARIO("inventario"),
	NOTIFICACIONES("notificaciones");

	private final String nombre;

	Microservicio(String nombre) {
		this.nombre = nombre;
	}

	public String nombre() {
		return nombre;
	}
}
