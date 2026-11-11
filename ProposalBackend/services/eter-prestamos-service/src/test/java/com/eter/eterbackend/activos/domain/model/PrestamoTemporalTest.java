package com.eter.eterbackend.activos.domain.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PrestamoTemporal (dominio)")
class PrestamoTemporalTest {

	private static final String NIT = "900123456-1";
	private static final String PLACA = "AF-2026-0001";
	private static final String DOCUMENTO = "79123456";

	private static PrestamoTemporal vigente() {
		LocalDateTime salida = LocalDateTime.now().minusDays(2);
		return PrestamoTemporal.nuevo(NIT, PLACA, DOCUMENTO, salida, salida.plusDays(10));
	}

	@Test
	@DisplayName("un prestamo nuevo nace VIGENTE y sin devolucion real")
	void naceVigente() {
		PrestamoTemporal prestamo = vigente();

		assertAll(
				() -> assertEquals(EstadoPrestamo.VIGENTE, prestamo.estado()),
				() -> assertNull(prestamo.fechaDevolucionReal()),
				() -> assertNull(prestamo.idPrestamo()),
				() -> assertTrue(prestamo.estaVigente()));
	}

	@Test
	@DisplayName("assetKey construye la llave que se usa en Kafka")
	void assetKeyCombinaNitYPlaca() {
		assertEquals(NIT + ":" + PLACA, vigente().assetKey());
	}

	@Nested
	@DisplayName("calculo de vencido")
	class Vencido {

		@Test
		@DisplayName("VIGENTE con fecha pactada ya pasada esta vencido")
		void vigenteConFechaPasada() {
			LocalDateTime salida = LocalDateTime.now().minusDays(10);
			PrestamoTemporal prestamo = PrestamoTemporal.nuevo(NIT, PLACA, DOCUMENTO,
					salida, salida.plusDays(3));

			assertTrue(prestamo.vencido());
		}

		@Test
		@DisplayName("VIGENTE con fecha pactada futura no esta vencido")
		void vigenteConFechaFutura() {
			assertFalse(vigente().vencido());
		}

		@Test
		@DisplayName("un prestamo DEVUELTO nunca esta vencido, aunque la fecha pactada haya pasado")
		void devueltoNoSeConsideraVencido() {
			LocalDateTime salida = LocalDateTime.now().minusDays(10);
			PrestamoTemporal devuelto = PrestamoTemporal
					.nuevo(NIT, PLACA, DOCUMENTO, salida, salida.plusDays(3))
					.registrarDevolucion(LocalDateTime.now());

			assertFalse(devuelto.vencido());
		}
	}

	@Nested
	@DisplayName("registro de devolucion")
	class Devolucion {

		@Test
		@DisplayName("pasa a DEVUELTO y guarda la fecha real")
		void registraDevolucion() {
			LocalDateTime ahora = LocalDateTime.now();
			PrestamoTemporal devuelto = vigente().registrarDevolucion(ahora);

			assertAll(
					() -> assertEquals(EstadoPrestamo.DEVUELTO, devuelto.estado()),
					() -> assertEquals(ahora, devuelto.fechaDevolucionReal()),
					() -> assertFalse(devuelto.estaVigente()));
		}

		@Test
		@DisplayName("no muta el prestamo original")
		void noMutaElOriginal() {
			PrestamoTemporal original = vigente();
			original.registrarDevolucion(LocalDateTime.now());

			assertAll(
					() -> assertEquals(EstadoPrestamo.VIGENTE, original.estado()),
					() -> assertNull(original.fechaDevolucionReal()));
		}

		@Test
		@DisplayName("rechaza una fecha de devolucion anterior a la salida")
		void rechazaFechaAnteriorALaSalida() {
			PrestamoTemporal prestamo = vigente();

			IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
					() -> prestamo.registrarDevolucion(prestamo.fechaSalida().minusDays(1)));

			assertEquals("La fechaDevolucionReal no puede ser anterior a la fechaSalida.",
					error.getMessage());
		}

		@Test
		@DisplayName("rechaza una fecha de devolucion nula")
		void rechazaFechaNula() {
			PrestamoTemporal prestamo = vigente();

			assertThrows(NullPointerException.class, () -> prestamo.registrarDevolucion(null));
		}
	}

	@Nested
	@DisplayName("rechaza datos invalidos")
	class DatosInvalidos {

		@Test
		@DisplayName("fecha pactada igual a la de salida")
		void fechaPactadaIgualALaSalida() {
			LocalDateTime salida = LocalDateTime.now();

			IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
					() -> PrestamoTemporal.nuevo(NIT, PLACA, DOCUMENTO, salida, salida));

			assertEquals("La fechaDevolucionPactada debe ser posterior a la fechaSalida.",
					error.getMessage());
		}

		@Test
		@DisplayName("fecha pactada anterior a la de salida")
		void fechaPactadaAnterior() {
			LocalDateTime salida = LocalDateTime.now();

			assertThrows(IllegalArgumentException.class,
					() -> PrestamoTemporal.nuevo(NIT, PLACA, DOCUMENTO, salida, salida.minusDays(1)));
		}

		@Test
		@DisplayName("numDocumento vacio")
		void documentoVacio() {
			LocalDateTime salida = LocalDateTime.now();

			IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
					() -> PrestamoTemporal.nuevo(NIT, PLACA, "  ", salida, salida.plusDays(1)));

			assertEquals("El campo numDocumento es obligatorio.", error.getMessage());
		}

		@Test
		@DisplayName("un DEVUELTO sin fechaDevolucionReal es inconsistente")
		void devueltoSinFechaReal() {
			LocalDateTime salida = LocalDateTime.now();

			IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
					() -> new PrestamoTemporal(null, NIT, PLACA, DOCUMENTO, salida,
							salida.plusDays(1), null, EstadoPrestamo.DEVUELTO));

			assertEquals("Un préstamo DEVUELTO debe tener fechaDevolucionReal.", error.getMessage());
		}
	}
}
