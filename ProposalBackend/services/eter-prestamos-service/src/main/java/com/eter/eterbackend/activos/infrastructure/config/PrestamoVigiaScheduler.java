package com.eter.eterbackend.activos.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.eter.eterbackend.activos.application.port.in.GenerarAvisosPrestamosVencidosUseCase;

/**
 * Vigia de prestamos. Es un adaptador de entrada mas: lo unico que hace es
 * despertar cada cierto tiempo y llamar al caso de uso. Toda la regla de
 * negocio vive en la capa de aplicacion, no aqui.
 */
@Component
public class PrestamoVigiaScheduler {

	private static final Logger log = LoggerFactory.getLogger(PrestamoVigiaScheduler.class);

	private final GenerarAvisosPrestamosVencidosUseCase generarAvisosUseCase;

	public PrestamoVigiaScheduler(GenerarAvisosPrestamosVencidosUseCase generarAvisosUseCase) {
		this.generarAvisosUseCase = generarAvisosUseCase;
	}

	@Scheduled(fixedDelayString = "${eter.prestamos.vigia-delay-ms}")
	public void revisarPrestamosVencidos() {
		int nuevos = generarAvisosUseCase.generarAvisos();
		if (nuevos > 0) {
			log.info("Vigía de préstamos: {} evento(s) PRESTAMO_VENCIDO registrados en el Outbox.", nuevos);
		}
	}
}
