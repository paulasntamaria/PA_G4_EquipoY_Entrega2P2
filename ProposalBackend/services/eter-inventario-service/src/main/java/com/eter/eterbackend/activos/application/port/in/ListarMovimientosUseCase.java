package com.eter.eterbackend.activos.application.port.in;

import java.util.List;

import com.eter.eterbackend.activos.domain.model.MovimientoInventario;
import com.eter.eterbackend.activos.domain.model.TipoMovimiento;

public interface ListarMovimientosUseCase {

	List<MovimientoInventario> listar(String nitUniv, String placaInventario, TipoMovimiento tipoMovimiento);
}
