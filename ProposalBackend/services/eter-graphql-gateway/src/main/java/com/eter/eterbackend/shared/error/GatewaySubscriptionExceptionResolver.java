package com.eter.eterbackend.shared.error;

import org.springframework.graphql.execution.SubscriptionExceptionResolverAdapter;
import org.springframework.stereotype.Component;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;

/** Suscripciones: si el flujo termina con una GatewayException (ej. token vencido), el cliente recibe su código. */
@Component
public class GatewaySubscriptionExceptionResolver extends SubscriptionExceptionResolverAdapter {

	@Override
	protected GraphQLError resolveToSingleError(Throwable ex) {
		if (ex instanceof GatewayException error) {
			return error.comoErrorGraphQL(GraphqlErrorBuilder.newError());
		}
		return null;
	}
}
