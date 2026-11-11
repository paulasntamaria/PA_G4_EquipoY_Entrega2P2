package com.eter.eterbackend.activos.infrastructure.adapter.in.rest.mapper;
import org.springframework.stereotype.Component;
import com.eter.eterbackend.activos.domain.model.ActivoFijo;
import com.eter.eterbackend.activos.infrastructure.adapter.in.rest.dto.ActivosDtos;
@Component
public class ActivosRestMapper {
 public ActivosDtos.ActivoFijoResponse toResponse(ActivoFijo a){return new ActivosDtos.ActivoFijoResponse(a.nitUniv(),a.placaInventario(),a.descripcion(),a.codUnspsc(),a.codSede(),a.valorContable(),a.estado());}
}
