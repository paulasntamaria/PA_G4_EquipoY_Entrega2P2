package com.eter.eterbackend.activos.infrastructure.adapter.in.rest.dto;
import static com.eter.eterbackend.activos.infrastructure.adapter.in.rest.dto.FormatosCampos.*;
import java.time.LocalDateTime; import java.util.UUID;
import com.eter.eterbackend.activos.domain.model.EstadoPrestamo; import jakarta.validation.constraints.*;
public final class ActivosDtos { private ActivosDtos(){}
 @RangoFechasPrestamo public record CrearPrestamoRequest(
   @NotBlank @Size(max=20) @Pattern(regexp=NIT, message=NIT_MENSAJE) String nitUniv,
   @NotBlank @Size(max=50) @Pattern(regexp=CODIGO, message=PLACA_MENSAJE) String placaInventario,
   @NotBlank @Pattern(regexp=DOCUMENTO, message=DOCUMENTO_MENSAJE) String numDocumento,
   @NotNull LocalDateTime fechaSalida,
   @NotNull LocalDateTime fechaDevolucionPactada){}
 public record RegistrarDevolucionRequest(@NotNull @PastOrPresent LocalDateTime fechaDevolucionReal){}
 public record PrestamoTemporalResponse(UUID idPrestamo,String nitUniv,String placaInventario,String numDocumento,LocalDateTime fechaSalida,LocalDateTime fechaDevolucionPactada,LocalDateTime fechaDevolucionReal,EstadoPrestamo estado,boolean vencido){}
}
