package com.eter.eterbackend.activos.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.eter.eterbackend.activos.domain.model.EstadoPrestamo;
import com.eter.eterbackend.activos.domain.model.PrestamoTemporal;

public interface PrestamoTemporalRepository {

	Optional<PrestamoTemporal> findById(UUID idPrestamo);

	List<PrestamoTemporal> buscar(String nitUniv, String placaInventario, EstadoPrestamo estado);

	/** Lo usa el vigia: prestamos VIGENTE cuya fecha pactada ya paso. */
	List<PrestamoTemporal> findVigentesVencidos(LocalDateTime corte);

	/** true si el activo ya tiene un prestamo VIGENTE. */
	boolean existeVigente(String nitUniv, String placaInventario);

	PrestamoTemporal save(PrestamoTemporal prestamoTemporal);
}
