package com.eter.eterbackend.activos.infrastructure.adapter.in.rest.dto;
import static com.eter.eterbackend.activos.infrastructure.adapter.in.rest.dto.FormatosCampos.*;
import java.time.LocalDateTime; import com.eter.eterbackend.activos.domain.model.TipoMovimiento; import jakarta.validation.constraints.*;
public final class ActivosDtos { private ActivosDtos(){}
 public record CrearAsignacionRequest(
   @NotBlank @Size(max=20) @Pattern(regexp=NIT, message=NIT_MENSAJE) String nitUniv,
   @NotBlank @Size(max=50) @Pattern(regexp=CODIGO, message=PLACA_MENSAJE) String placaInventario,
   @NotBlank @Pattern(regexp=DOCUMENTO, message=DOCUMENTO_MENSAJE) String numDocumento,
   @NotNull @PastOrPresent LocalDateTime fechaHoraActa){}
 public record AsignacionActivoResponse(String nitUniv,String placaInventario,String numDocumento,LocalDateTime fechaHoraActa){}
 public record CrearMovimientoRequest(
   @NotBlank @Size(max=20) @Pattern(regexp=NIT, message=NIT_MENSAJE) String nitUniv,
   @NotBlank @Size(max=50) @Pattern(regexp=CODIGO, message=PLACA_MENSAJE) String placaInventario,
   @NotNull TipoMovimiento tipoMovimiento,
   @NotNull @PastOrPresent LocalDateTime fechaMovimiento,
   @NotBlank @Pattern(regexp=DOCUMENTO, message=DOCUMENTO_MENSAJE) String numDocumento){}
 public record MovimientoInventarioResponse(Long idMovimiento,String nitUniv,String placaInventario,TipoMovimiento tipoMovimiento,LocalDateTime fechaMovimiento,String numDocumento){}
}
