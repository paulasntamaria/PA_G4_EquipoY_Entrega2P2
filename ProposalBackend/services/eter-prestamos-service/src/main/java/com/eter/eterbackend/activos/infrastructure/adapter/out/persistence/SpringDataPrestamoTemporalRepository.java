package com.eter.eterbackend.activos.infrastructure.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.eter.eterbackend.activos.domain.model.EstadoPrestamo;

public interface SpringDataPrestamoTemporalRepository extends JpaRepository<PrestamoTemporalJpaEntity, UUID> {

	@Query("""
			SELECT p FROM PrestamoTemporalJpaEntity p
			WHERE p.nitUniv = :nitUniv
			  AND (:placaInventario IS NULL OR p.placaInventario = :placaInventario)
			  AND (:estado IS NULL OR p.estado = :estado)
			ORDER BY p.fechaSalida DESC
			""")
	List<PrestamoTemporalJpaEntity> buscar(@Param("nitUniv") String nitUniv,
			@Param("placaInventario") String placaInventario,
			@Param("estado") EstadoPrestamo estado);

	/** Consulta del vigia: solo VIGENTE, sin devolucion real y ya pasados de fecha. */
	@Query("""
			SELECT p FROM PrestamoTemporalJpaEntity p
			WHERE p.estado = com.eter.eterbackend.activos.domain.model.EstadoPrestamo.VIGENTE
			  AND p.fechaDevolucionReal IS NULL
			  AND p.fechaDevolucionPactada < :corte
			""")
	List<PrestamoTemporalJpaEntity> findVigentesVencidos(@Param("corte") LocalDateTime corte);

	boolean existsByNitUnivAndPlacaInventarioAndEstado(String nitUniv, String placaInventario,
			EstadoPrestamo estado);
}
