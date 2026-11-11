package com.eter.eterbackend.notificaciones.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.eter.eterbackend.notificaciones.application.service.NotificacionService;
import com.eter.eterbackend.notificaciones.domain.repository.NotificacionRepository;

@Configuration
public class NotificacionesConfig {

	@Bean
	public NotificacionService notificacionService(NotificacionRepository notificacionRepository) {
		return new NotificacionService(notificacionRepository);
	}
}
