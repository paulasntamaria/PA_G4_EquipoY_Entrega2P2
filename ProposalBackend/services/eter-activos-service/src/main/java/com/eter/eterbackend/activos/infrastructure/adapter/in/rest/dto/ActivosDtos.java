package com.eter.eterbackend.activos.infrastructure.adapter.in.rest.dto;
import static com.eter.eterbackend.activos.infrastructure.adapter.in.rest.dto.FormatosCampos.*;
import java.math.BigDecimal;
import com.eter.eterbackend.activos.domain.model.EstadoActivo;
import jakarta.validation.constraints.*;
public final class ActivosDtos {
    private ActivosDtos() {}
    public record CrearActivoFijoRequest(
        @NotBlank @Size(max=20) @Pattern(regexp=NIT, message=NIT_MENSAJE) String nitUniv,
        @NotBlank @Size(max=50) @Pattern(regexp=CODIGO, message=PLACA_MENSAJE) String placaInventario,
        @NotBlank @Size(min=3, max=255) String descripcion,
        @NotBlank @Pattern(regexp=UNSPSC, message=UNSPSC_MENSAJE) String codUnspsc,
        @NotBlank @Size(max=20) @Pattern(regexp=CODIGO, message=SEDE_MENSAJE) String codSede,
        @NotNull @DecimalMin("0.0") @Digits(integer=15, fraction=4) BigDecimal valorContable,
        @NotNull EstadoActivo estado) {}
    public record ActualizarActivoFijoRequest(
        @NotBlank @Size(min=3, max=255) String descripcion,
        @NotBlank @Pattern(regexp=UNSPSC, message=UNSPSC_MENSAJE) String codUnspsc,
        @NotBlank @Size(max=20) @Pattern(regexp=CODIGO, message=SEDE_MENSAJE) String codSede,
        @NotNull @DecimalMin("0.0") @Digits(integer=15, fraction=4) BigDecimal valorContable,
        @NotNull EstadoActivo estado) {}
    public record ActivoFijoResponse(String nitUniv,String placaInventario,String descripcion,String codUnspsc,
        String codSede,BigDecimal valorContable,EstadoActivo estado) {}
}
