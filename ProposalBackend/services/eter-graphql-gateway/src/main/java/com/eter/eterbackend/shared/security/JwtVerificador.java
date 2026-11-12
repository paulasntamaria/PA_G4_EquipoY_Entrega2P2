package com.eter.eterbackend.shared.security;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Valida el JWT (HS256) con el mismo secreto que los microservicios. */
@Component
public class JwtVerificador {

	private static final String PREFIJO_BEARER = "Bearer ";

	private final SecretKey clave;

	public JwtVerificador(@Value("${eter.jwt.secret}") String secreto) {
		this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
	}

	/** Devuelve el usuario del token, o null si falta, está vencido o la firma no es válida. */
	public UsuarioAutenticado autenticar(String cabeceraAuthorization) {
		if (cabeceraAuthorization == null || !cabeceraAuthorization.startsWith(PREFIJO_BEARER)) {
			return null;
		}
		String token = cabeceraAuthorization.substring(PREFIJO_BEARER.length()).trim();
		try {
			Claims claims = Jwts.parser().verifyWith(clave).build().parseSignedClaims(token).getPayload();
			return new UsuarioAutenticado(token, claims.getSubject(), claims.get("nitUniv", String.class), leerRoles(claims));
		} catch (JwtException | IllegalArgumentException ex) {
			return null;
		}
	}

	private List<String> leerRoles(Claims claims) {
		if (claims.get("roles") instanceof List<?> lista) {
			return lista.stream().map(String::valueOf).toList();
		}
		return List.of();
	}
}
