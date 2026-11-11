package com.eter.eterbackend.shared.security;

/** Datos que se leen del JWT. */
public record UsuarioAutenticado(String email, String nitUniv) {
}
