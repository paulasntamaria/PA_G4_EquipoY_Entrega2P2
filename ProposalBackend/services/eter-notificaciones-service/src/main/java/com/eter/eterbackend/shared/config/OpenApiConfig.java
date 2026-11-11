package com.eter.eterbackend.shared.config;

import java.util.List;
import java.util.Map;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI api() {
		return new OpenAPI().info(new Info().title("ETER - Microservicio de Notificaciones").version("1.0.0"))
				.components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
						.type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
	}

	/** Documenta en el contrato los errores que puede devolver cada endpoint. */
	@Bean
	OpenApiCustomizer respuestasDeError() {
		return openApi -> ContratoErroresOpenApi.documentar(openApi, Map.of(
				"marcarNotificacionLeida", List.of("404")));
	}
}
