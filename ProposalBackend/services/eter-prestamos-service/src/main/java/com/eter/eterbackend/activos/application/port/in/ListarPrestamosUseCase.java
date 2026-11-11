package com.eter.eterbackend.activos.application.port.in;

import java.util.List;

import com.eter.eterbackend.activos.domain.model.EstadoPrestamo;
import com.eter.eterbackend.activos.domain.model.PrestamoTemporal;

public interface ListarPrestamosUseCase {

	List<PrestamoTemporal> listar(String nitUniv, String placaInventario, EstadoPrestamo estado);
}
