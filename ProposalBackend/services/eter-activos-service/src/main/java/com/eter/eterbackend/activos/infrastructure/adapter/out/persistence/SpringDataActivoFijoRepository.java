package com.eter.eterbackend.activos.infrastructure.adapter.out.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataActivoFijoRepository extends JpaRepository<ActivoFijoJpaEntity, ActivoFijoId> {

	List<ActivoFijoJpaEntity> findByNitUnivOrderByPlacaInventarioAsc(String nitUniv);
}
