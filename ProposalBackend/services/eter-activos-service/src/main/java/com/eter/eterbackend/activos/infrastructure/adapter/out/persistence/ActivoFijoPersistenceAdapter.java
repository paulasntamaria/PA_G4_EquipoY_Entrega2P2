package com.eter.eterbackend.activos.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.eter.eterbackend.activos.domain.model.ActivoFijo;
import com.eter.eterbackend.activos.domain.repository.ActivoFijoRepository;

/** Adaptador de salida: implementa el puerto del dominio usando JPA. */
@Component
public class ActivoFijoPersistenceAdapter implements ActivoFijoRepository {

	private final SpringDataActivoFijoRepository repositorio;
	private final ActivoFijoPersistenceMapper mapper;

	public ActivoFijoPersistenceAdapter(SpringDataActivoFijoRepository repositorio,
			ActivoFijoPersistenceMapper mapper) {
		this.repositorio = repositorio;
		this.mapper = mapper;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean existsById(String nitUniv, String placaInventario) {
		return repositorio.existsById(new ActivoFijoId(nitUniv, placaInventario));
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ActivoFijo> findById(String nitUniv, String placaInventario) {
		return repositorio.findById(new ActivoFijoId(nitUniv, placaInventario)).map(mapper::toDomain);
	}

	@Override
	@Transactional(readOnly = true)
	public List<ActivoFijo> findByNitUniv(String nitUniv) {
		return repositorio.findByNitUnivOrderByPlacaInventarioAsc(nitUniv).stream()
				.map(mapper::toDomain)
				.toList();
	}

	@Override
	@Transactional
	public ActivoFijo save(ActivoFijo activoFijo) {
		return mapper.toDomain(repositorio.save(mapper.toEntity(activoFijo)));
	}
}
