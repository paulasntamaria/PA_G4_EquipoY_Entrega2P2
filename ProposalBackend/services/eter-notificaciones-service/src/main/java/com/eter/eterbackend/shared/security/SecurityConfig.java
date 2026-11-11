package com.eter.eterbackend.shared.security;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.firewall.RequestRejectedHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Seguridad JWT: escritura por rol y aislamiento por universidad. */
@Configuration
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final List<String> rolesEscritura;

	public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
			@Value("${eter.security.roles-escritura:}") List<String> rolesEscritura) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.rolesEscritura = rolesEscritura.stream().map(String::trim).filter(r -> !r.isEmpty()).toList();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> {
					auth.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
							"/actuator/health", "/error").permitAll();
					if (rolesEscritura.isEmpty()) {
						auth.anyRequest().authenticated();
					} else {
						auth.requestMatchers(HttpMethod.GET, "/api/**").authenticated();
						auth.anyRequest().hasAnyRole(rolesEscritura.toArray(String[]::new));
					}
				})
				// 401 y 403 en formato ProblemDetail.
				.exceptionHandling(e -> e
						.authenticationEntryPoint((request, response, ex) -> escribirProblema(request, response,
								HttpStatus.UNAUTHORIZED, "No autenticado",
								"Debe enviar un token JWT válido en la cabecera Authorization."))
						.accessDeniedHandler((request, response, ex) -> escribirProblema(request, response,
								HttpStatus.FORBIDDEN, "Acceso denegado",
								"Su rol no tiene permiso para realizar esta operación.")))
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.build();
	}

	/** URLs rechazadas por el firewall tambien responden con ProblemDetail. */
	@Bean
	public RequestRejectedHandler requestRejectedHandler() {
		return (request, response, ex) -> escribirProblema(request, response, HttpStatus.BAD_REQUEST,
				"Solicitud inválida", "La URL contiene caracteres no permitidos.");
	}

	/** Usuario del token actual. */
	public static UsuarioAutenticado usuarioActual() {
		var autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
			return usuario;
		}
		throw new IllegalStateException("No hay un usuario autenticado en el contexto.");
	}

	/** El nitUniv de la peticion debe ser el del token; si no, 403. */
	public static void verificarTenant(String nitUniv) {
		String nitToken = usuarioActual().nitUniv();
		if (nitToken == null || nitUniv == null || !nitToken.trim().equals(nitUniv.trim())) {
			throw new TenantNoAutorizadoException(
					"No tiene permiso para operar sobre la universidad %s.".formatted(nitUniv));
		}
	}

	// Se escribe a mano: corre antes de Spring MVC.
	private static void escribirProblema(HttpServletRequest request, HttpServletResponse response,
			HttpStatus estado, String titulo, String detalle) throws IOException {
		response.setStatus(estado.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write("""
				{"type":"about:blank","title":"%s","status":%d,"detail":"%s","instance":"%s"}"""
				.formatted(titulo, estado.value(), detalle, escaparJson(request.getRequestURI())));
	}

	private static String escaparJson(String valor) {
		return valor == null ? "" : valor.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
