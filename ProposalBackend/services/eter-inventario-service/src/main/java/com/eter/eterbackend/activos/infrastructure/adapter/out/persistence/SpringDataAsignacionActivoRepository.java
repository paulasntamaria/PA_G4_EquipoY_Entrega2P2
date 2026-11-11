package com.eter.eterbackend.activos.infrastructure.adapter.out.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataAsignacionActivoRepository
		extends JpaRepository<AsignacionActivoJpaEntity, AsignacionActivoId> {

	// Los filtros de placa y documento son opcionales: si llegan nulos no filtran.
	@Query("""
			SELECT a FROM AsignacionActivoJpaEntity a
			WHERE a.nitUniv = :nitUniv
			  AND (:placaInventario IS NULL OR a.placaInventario = :placaInventario)
			  AND (:numDocumento IS NULL OR a.numDocumento = :numDocumento)
			ORDER BY a.fechaHoraActa DESC
			""")
	List<AsignacionActivoJpaEntity> buscar(@Param("nitUniv") String nitUniv,
			@Param("placaInventario") String placaInventario,
			@Param("numDocumento") String numDocumento);
}
