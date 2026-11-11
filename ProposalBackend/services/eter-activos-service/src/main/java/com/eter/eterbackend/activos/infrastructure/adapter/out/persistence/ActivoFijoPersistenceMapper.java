package com.eter.eterbackend.activos.infrastructure.adapter.out.persistence;

import org.springframework.stereotype.Component;

import com.eter.eterbackend.activos.domain.model.ActivoFijo;

/** Traduce entre el modelo de dominio y la entidad JPA. */
@Component
public class ActivoFijoPersistenceMapper {

	public ActivoFijo toDomain(ActivoFijoJpaEntity entity) {
		return new ActivoFijo(entity.getNitUniv(), entity.getPlacaInventario(), entity.getDescripcion(),
				entity.getCodUnspsc(), entity.getCodSede(), entity.getValorContable(), entity.getEstado());
	}

	public ActivoFijoJpaEntity toEntity(ActivoFijo activoFijo) {
		return new ActivoFijoJpaEntity(activoFijo.nitUniv(), activoFijo.placaInventario(),
				activoFijo.descripcion(), activoFijo.codUnspsc(), activoFijo.codSede(),
				activoFijo.valorContable(), activoFijo.estado());
	}
}
