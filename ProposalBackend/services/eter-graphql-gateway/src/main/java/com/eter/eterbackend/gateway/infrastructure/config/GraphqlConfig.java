package com.eter.eterbackend.gateway.infrastructure.config;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.GraphQLScalarType;

/** Registra el escalar DateTime del esquema (src/main/resources/graphql/schema.graphqls). */
@Configuration
public class GraphqlConfig {

	private static final String MENSAJE = "DateTime invalido: use ISO-8601 (2026-05-01T08:00:00).";

	/**
	 * Fecha y hora ISO-8601 como texto. Se valida el formato y se reenvía tal cual: cada
	 * microservicio la interpreta (LocalDateTime) igual que antes.
	 */
	static final GraphQLScalarType DATE_TIME = GraphQLScalarType.newScalar()
			.name("DateTime")
			.description("Fecha y hora ISO-8601 (ej. 2026-05-01T08:00:00).")
			.coercing(new Coercing<String, String>() {
				@Override
				public String serialize(Object valor, GraphQLContext contexto, Locale locale) {
					return String.valueOf(valor);
				}

				@Override
				public String parseValue(Object entrada, GraphQLContext contexto, Locale locale) {
					if (entrada instanceof String texto && esFechaIso(texto)) {
						return texto;
					}
					throw new CoercingParseValueException(MENSAJE);
				}

				@Override
				public String parseLiteral(Value<?> entrada, CoercedVariables variables, GraphQLContext contexto, Locale locale) {
					if (entrada instanceof StringValue texto && esFechaIso(texto.getValue())) {
						return texto.getValue();
					}
					throw new CoercingParseLiteralException(MENSAJE);
				}
			})
			.build();

	@Bean
	RuntimeWiringConfigurer escalares() {
		return wiring -> wiring.scalar(DATE_TIME);
	}

	/** Acepta 2026-05-01T08:00:00, 2026-05-01T08:00, con zona (…-05:00 o …Z) o solo la fecha. */
	private static final List<Function<String, Object>> FORMATOS_ISO =
			List.of(LocalDateTime::parse, OffsetDateTime::parse, Instant::parse, LocalDate::parse);

	static boolean esFechaIso(String texto) {
		for (Function<String, Object> lector : FORMATOS_ISO) {
			try {
				lector.apply(texto);
				return true;
			} catch (DateTimeParseException ex) {
				// probar el siguiente formato
			}
		}
		return false;
	}
}
