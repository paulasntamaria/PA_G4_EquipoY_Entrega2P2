package com.eter.eterbackend.shared.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Valida el JWT Bearer que exige el contrato (securitySchemes.bearerAuth).
 * Firma HS256 con el secreto compartido. Si el token es valido deja en el
 * contexto de seguridad un UsuarioAutenticado con el correo y el nitUniv.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String PREFIJO_BEARER = "Bearer ";

	private final SecretKey clave;

	public JwtAuthenticationFilter(@Value("${eter.jwt.secret}") String secreto) {
		this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {

		String cabecera = request.getHeader(HttpHeaders.AUTHORIZATION);

		if (cabecera != null && cabecera.startsWith(PREFIJO_BEARER)) {
			String token = cabecera.substring(PREFIJO_BEARER.length()).trim();
			try {
				Claims claims = Jwts.parser()
						.verifyWith(clave)
						.build()
						.parseSignedClaims(token)
						.getPayload();

				UsuarioAutenticado usuario = new UsuarioAutenticado(
						claims.getSubject(),
						claims.get("nitUniv", String.class));

				List<SimpleGrantedAuthority> roles = leerRoles(claims);

				var autenticacion = new UsernamePasswordAuthenticationToken(usuario, null, roles);
				SecurityContextHolder.getContext().setAuthentication(autenticacion);
			} catch (JwtException | IllegalArgumentException ex) {
				// Token invalido: se deja el contexto vacio y Spring Security responde 401.
				SecurityContextHolder.clearContext();
			}
		}

		chain.doFilter(request, response);
	}

	@SuppressWarnings("unchecked")
	private List<SimpleGrantedAuthority> leerRoles(Claims claims) {
		Object valor = claims.get("roles");
		if (valor instanceof List<?> lista) {
			return ((List<String>) lista).stream()
					.map(rol -> new SimpleGrantedAuthority(rol.startsWith("ROLE_") ? rol : "ROLE_" + rol))
					.toList();
		}
		return List.of();
	}
}
