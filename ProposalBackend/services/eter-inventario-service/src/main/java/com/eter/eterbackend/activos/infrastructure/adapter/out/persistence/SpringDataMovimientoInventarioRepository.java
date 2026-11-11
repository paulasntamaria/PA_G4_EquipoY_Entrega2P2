package com.eter.eterbackend.activos.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.eter.eterbackend.activos.domain.model.TipoMovimiento;

public interface SpringDataMovimientoInventarioRepository
		extends JpaRepository<MovimientoInventarioJpaEntity, Long> {

	@Query("""
			SELECT m FROM MovimientoInventarioJpaEntity m
			WHERE m.nitUniv = :nitUniv
			  AND (:placaInventario IS NULL OR m.placaInventario = :placaInventario)
			  AND (:tipoMovimiento IS NULL OR m.tipoMovimiento = :tipoMovimiento)
			ORDER BY m.fechaMovimiento DESC
			""")
	List<MovimientoInventarioJpaEntity> buscar(@Param("nitUniv") String nitUniv,
			@Param("placaInventario") String placaInventario,
			@Param("tipoMovimiento") TipoMovimiento tipoMovimiento);

	Optional<MovimientoInventarioJpaEntity> findByNitUnivAndIdempotencyKey(String nitUniv, String idempotencyKey);
}
