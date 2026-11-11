package com.eter.eterbackend.activos.infrastructure.config;
import org.springframework.context.annotation.*; import com.eter.eterbackend.activos.application.service.*; import com.eter.eterbackend.activos.domain.repository.*; import com.eter.eterbackend.shared.activos.ActivoExistencePort;
@Configuration public class InventarioConfig {
 @Bean AsignacionActivoService asignacion(AsignacionActivoRepository r,ActivoExistencePort a){return new AsignacionActivoService(r,a);}
 @Bean MovimientoInventarioService movimiento(MovimientoInventarioRepository r,ActivoExistencePort a){return new MovimientoInventarioService(r,a);}
}
