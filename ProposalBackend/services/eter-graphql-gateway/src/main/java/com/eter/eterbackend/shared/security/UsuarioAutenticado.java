package com.eter.eterbackend.shared.security;

import java.util.List;

/** Datos que se leen del JWT. El token se guarda para reenviarlo tal cual a los microservicios. */
public record UsuarioAutenticado(String token, String email, String nitUniv, List<String> roles) {
}
