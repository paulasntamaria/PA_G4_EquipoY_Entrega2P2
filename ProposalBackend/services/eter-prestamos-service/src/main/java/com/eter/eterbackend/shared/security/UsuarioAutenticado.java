package com.eter.eterbackend.shared.security;

/**
 * Datos que el microservicio extrae del JWT. El contrato OpenAPI dice que el
 * tenant y el destinatario de las notificaciones se derivan del token y no se
 * reciben como parametros, asi que aqui es donde se leen.
 */
public record UsuarioAutenticado(String email, String nitUniv) {
}
