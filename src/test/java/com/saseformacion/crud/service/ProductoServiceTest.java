package com.saseformacion.crud.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.saseformacion.crud.exception.ProductoNoEncontradoException;
import com.saseformacion.crud.model.Producto;

// @Transactional deshace los cambios al terminar cada test: la base de datos queda como estaba
@SpringBootTest
@Transactional
class ProductoServiceTest {

	@Autowired
	private ProductoService productoService;

	@Test
	void crearYBuscarPorId() {
		Producto creado = productoService.crear(new Producto("Teclado", "Teclado mecánico", new BigDecimal("49.99"), 10));

		assertNotNull(creado.getId());
		assertEquals("Teclado", productoService.buscarPorId(creado.getId()).getNombre());
	}

	@Test
	void actualizar() {
		Producto creado = productoService.crear(new Producto("Ratón", null, new BigDecimal("19.90"), 5));

		Producto actualizado = productoService.actualizar(creado.getId(), new Producto("Ratón inalámbrico", null, new BigDecimal("24.90"), 3));

		assertEquals("Ratón inalámbrico", actualizado.getNombre());
		assertEquals(3, actualizado.getStock());
	}

	@Test
	void eliminar() {
		Producto creado = productoService.crear(new Producto("Monitor", null, new BigDecimal("199.00"), 2));

		productoService.eliminar(creado.getId());

		assertThrows(ProductoNoEncontradoException.class, () -> productoService.buscarPorId(creado.getId()));
	}

	@Test
	void buscarIdInexistenteLanzaExcepcion() {
		assertThrows(ProductoNoEncontradoException.class, () -> productoService.buscarPorId(-1L));
		assertThrows(ProductoNoEncontradoException.class, () -> productoService.eliminar(-1L));
	}

}
