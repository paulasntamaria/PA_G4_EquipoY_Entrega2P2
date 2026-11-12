package com.eter.eterbackend.shared.error;

import java.util.concurrent.CompletionException;

import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.stereotype.Component;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;

/**
 * Queries y mutations: una GatewayException se entrega con su código. Cualquier otra excepción
 * queda como INTERNAL_ERROR sin detalles internos (Spring la registra en el log).
 */
@Component
public class GatewayExceptionResolver extends DataFetcherExceptionResolverAdapter {

	@Override
	protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
		Throwable causa = ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
		if (causa instanceof GatewayException error) {
			return error.comoErrorGraphQL(GraphqlErrorBuilder.newError(env));
		}
		return null;
	}
}
