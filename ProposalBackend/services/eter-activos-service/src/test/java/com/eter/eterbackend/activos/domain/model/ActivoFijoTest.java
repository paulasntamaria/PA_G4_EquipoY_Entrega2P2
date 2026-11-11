package com.eter.eterbackend.activos.domain.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ActivoFijo (dominio)")
class ActivoFijoTest {

	private static ActivoFijo valido() {
		return new ActivoFijo("900123456-1", "AF-2026-0001", "Computador portatil HP EliteBook",
				"43211503", "SED-01", new BigDecimal("3500000"), EstadoActivo.ACTIVO);
	}

	@Test
	@DisplayName("se construye con datos validos y conserva cada campo")
	void seConstruyeConDatosValidos() {
		ActivoFijo activo = valido();

		assertAll(
				() -> assertEquals("900123456-1", activo.nitUniv()),
				() -> assertEquals("AF-2026-0001", activo.placaInventario()),
				() -> assertEquals("SED-01", activo.codSede()),
				() -> assertEquals(new BigDecimal("3500000"), activo.valorContable()),
				() -> assertEquals(EstadoActivo.ACTIVO, activo.estado()));
	}

	@Test
	@DisplayName("recorta los espacios sobrantes de los campos de texto")
	void recortaEspacios() {
		ActivoFijo activo = new ActivoFijo("  900123456-1  ", "  AF-2026-0009  ", "  Escaner  ",
				"43211503", "SED-01", BigDecimal.TEN, EstadoActivo.ACTIVO);

		assertAll(
				() -> assertEquals("900123456-1", activo.nitUniv()),
				() -> assertEquals("AF-2026-0009", activo.placaInventario()),
				() -> assertEquals("Escaner", activo.descripcion()));
	}

	@Test
	@DisplayName("assetKey construye la llave que se usa en Kafka")
	void assetKeyCombinaNitYPlaca() {
		assertEquals("900123456-1:AF-2026-0001", valido().assetKey());
	}

	@Test
	@DisplayName("darDeBaja cambia el estado y no muta el original")
	void darDeBajaEsInmutable() {
		ActivoFijo original = valido();
		ActivoFijo dadoDeBaja = original.darDeBaja();

		assertAll(
				() -> assertEquals(EstadoActivo.BAJA, dadoDeBaja.estado()),
				() -> assertEquals(EstadoActivo.ACTIVO, original.estado()),
				() -> assertEquals(original.placaInventario(), dadoDeBaja.placaInventario()),
				() -> assertNotSame(original, dadoDeBaja));
	}

	@Nested
	@DisplayName("rechaza datos invalidos")
	class DatosInvalidos {

		@Test
		@DisplayName("placaInventario vacia")
		void placaVacia() {
			IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
					() -> new ActivoFijo("900123456-1", "   ", "Escaner", "43211503", "SED-01",
							BigDecimal.TEN, EstadoActivo.ACTIVO));

			assertEquals("El campo placaInventario es obligatorio.", error.getMessage());
		}

		@Test
		@DisplayName("nitUniv nulo")
		void nitNulo() {
			assertThrows(IllegalArgumentException.class,
					() -> new ActivoFijo(null, "AF-2026-0001", "Escaner", "43211503", "SED-01",
							BigDecimal.TEN, EstadoActivo.ACTIVO));
		}

		@Test
		@DisplayName("descripcion que excede los 255 caracteres")
		void descripcionDemasiadoLarga() {
			String larga = "x".repeat(256);

			IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
					() -> new ActivoFijo("900123456-1", "AF-2026-0001", larga, "43211503", "SED-01",
							BigDecimal.TEN, EstadoActivo.ACTIVO));

			assertEquals("El campo descripcion excede la longitud máxima de 255 caracteres.",
					error.getMessage());
		}

		@Test
		@DisplayName("valorContable negativo")
		void valorNegativo() {
			IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
					() -> new ActivoFijo("900123456-1", "AF-2026-0001", "Escaner", "43211503", "SED-01",
							new BigDecimal("-1"), EstadoActivo.ACTIVO));

			assertEquals("El campo valorContable no puede ser negativo.", error.getMessage());
		}

		@Test
		@DisplayName("valorContable en cero si se acepta")
		void valorCeroEsValido() {
			ActivoFijo activo = new ActivoFijo("900123456-1", "AF-2026-0010", "Donacion", "43211503",
					"SED-01", BigDecimal.ZERO, EstadoActivo.ACTIVO);

			assertEquals(BigDecimal.ZERO, activo.valorContable());
		}

		@Test
		@DisplayName("estado nulo")
		void estadoNulo() {
			assertThrows(NullPointerException.class,
					() -> new ActivoFijo("900123456-1", "AF-2026-0001", "Escaner", "43211503", "SED-01",
							BigDecimal.TEN, null));
		}
	}
}
