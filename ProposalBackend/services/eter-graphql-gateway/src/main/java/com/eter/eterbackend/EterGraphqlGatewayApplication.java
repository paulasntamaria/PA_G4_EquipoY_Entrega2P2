package com.eter.eterbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway GraphQL de ETER: único punto de entrada del frontend.
 * Traduce cada operación GraphQL a las APIs REST de los 4 microservicios
 * (activos 8087, préstamos 8088, inventario 8089, notificaciones 8090).
 */
@SpringBootApplication
public class EterGraphqlGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(EterGraphqlGatewayApplication.class, args);
	}
}
