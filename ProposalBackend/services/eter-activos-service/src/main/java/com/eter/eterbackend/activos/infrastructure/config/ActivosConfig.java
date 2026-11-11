package com.eter.eterbackend.activos.infrastructure.config;
import org.springframework.context.annotation.*;
import com.eter.eterbackend.activos.application.port.out.ActorActualPort;
import com.eter.eterbackend.activos.application.service.ActivoFijoService;
import com.eter.eterbackend.activos.domain.repository.ActivoFijoRepository;
import com.eter.eterbackend.shared.events.domain.EventoOutboxRepository;
@Configuration
public class ActivosConfig {
 @Bean ActivoFijoService activoFijoService(ActivoFijoRepository r, EventoOutboxRepository e, ActorActualPort a){return new ActivoFijoService(r,e,a);}
}
