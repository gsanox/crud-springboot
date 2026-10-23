package com.saseformacion.crud.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.saseformacion.crud.dto.ProductoRequest;
import com.saseformacion.crud.dto.ProductoResponse;
import com.saseformacion.crud.model.Producto;

// Test unitario puro: sin Spring ni base de datos, el mapper se crea con new
class ProductoMapperTest {

	private final ProductoMapper mapper = new ProductoMapper();

	@Test
	void toEntityCopiaLosDatosYDejaSinIdNiFecha() {
		ProductoRequest request = new ProductoRequest("Teclado", "Mecánico", new BigDecimal("49.99"), 10);

		Producto producto = mapper.toEntity(request);

		assertNull(producto.getId());
		assertNull(producto.getFechaCreacion());
		assertEquals("Teclado", producto.getNombre());
		assertEquals(new BigDecimal("49.99"), producto.getPrecio());
		assertEquals(10, producto.getStock());
	}

	@Test
	void toResponseCalculaDisponible() {
		Producto producto = new Producto("Ratón", null, new BigDecimal("19.90"), 0);
		producto.setId(3L);
		producto.setFechaCreacion(LocalDateTime.of(2026, 10, 23, 10, 0));

		ProductoResponse response = mapper.toResponse(producto);

		assertEquals(3L, response.id());
		assertEquals(LocalDateTime.of(2026, 10, 23, 10, 0), response.fechaCreacion());
		assertFalse(response.disponible());

		producto.setStock(5);
		assertTrue(mapper.toResponse(producto).disponible());
	}

	@Test
	void updateEntityNoTocaElIdNiLaFecha() {
		Producto producto = new Producto("Ratón", null, new BigDecimal("19.90"), 5);
		producto.setId(3L);
		LocalDateTime fecha = LocalDateTime.of(2026, 10, 23, 10, 0);
		producto.setFechaCreacion(fecha);

		mapper.updateEntity(producto, new ProductoRequest("Ratón inalámbrico", "Bluetooth", new BigDecimal("24.90"), 2));

		assertEquals(3L, producto.getId());
		assertEquals(fecha, producto.getFechaCreacion());
		assertEquals("Ratón inalámbrico", producto.getNombre());
		assertEquals(2, producto.getStock());
	}

}
