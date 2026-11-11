package com.eter.eterbackend.activos.infrastructure.adapter.in.rest.mapper;
import org.springframework.stereotype.Component; import com.eter.eterbackend.activos.domain.model.*; import com.eter.eterbackend.activos.infrastructure.adapter.in.rest.dto.ActivosDtos;
@Component public class ActivosRestMapper {
 public ActivosDtos.AsignacionActivoResponse toResponse(AsignacionActivo a){return new ActivosDtos.AsignacionActivoResponse(a.nitUniv(),a.placaInventario(),a.numDocumento(),a.fechaHoraActa());}
 public ActivosDtos.MovimientoInventarioResponse toResponse(MovimientoInventario m){return new ActivosDtos.MovimientoInventarioResponse(m.idMovimiento(),m.nitUniv(),m.placaInventario(),m.tipoMovimiento(),m.fechaMovimiento(),m.numDocumento());}
}
