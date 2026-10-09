package com.saseformacion.crud.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.saseformacion.crud.exception.ProductoNoEncontradoException;
import com.saseformacion.crud.model.Producto;
import com.saseformacion.crud.service.ProductoService;

// @WebMvcTest solo carga el controlador; el servicio se sustituye por un mock (no hace falta base de datos)
@WebMvcTest(ProductoController.class)
class ProductoControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProductoService productoService;

	private Producto teclado() {
		Producto producto = new Producto("Teclado", "Teclado mecánico", new BigDecimal("49.99"), 10);
		producto.setId(1L);
		return producto;
	}

	@Test
	void listar() throws Exception {
		when(productoService.listar()).thenReturn(List.of(teclado()));

		mockMvc.perform(get("/api/productos"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].nombre").value("Teclado"));
	}

	@Test
	void buscarPorId() throws Exception {
		when(productoService.buscarPorId(1L)).thenReturn(teclado());

		mockMvc.perform(get("/api/productos/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.precio").value(49.99));
	}

	@Test
	void buscarPorIdInexistenteDevuelve404() throws Exception {
		when(productoService.buscarPorId(99L)).thenThrow(new ProductoNoEncontradoException(99L));

		mockMvc.perform(get("/api/productos/99"))
				.andExpect(status().isNotFound());
	}

	@Test
	void crearDevuelve201() throws Exception {
		when(productoService.crear(any(Producto.class))).thenReturn(teclado());

		mockMvc.perform(post("/api/productos")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nombre": "Teclado", "descripcion": "Teclado mecánico", "precio": 49.99, "stock": 10}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(1));
	}

	@Test
	void actualizar() throws Exception {
		when(productoService.actualizar(eq(1L), any(Producto.class))).thenReturn(teclado());

		mockMvc.perform(put("/api/productos/1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nombre": "Teclado", "descripcion": "Teclado mecánico", "precio": 49.99, "stock": 10}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Teclado"));
	}

	@Test
	void eliminarDevuelve204() throws Exception {
		mockMvc.perform(delete("/api/productos/1"))
				.andExpect(status().isNoContent());

		verify(productoService).eliminar(1L);
	}

	@Test
	void eliminarInexistenteDevuelve404() throws Exception {
		doThrow(new ProductoNoEncontradoException(99L)).when(productoService).eliminar(99L);

		mockMvc.perform(delete("/api/productos/99"))
				.andExpect(status().isNotFound());
	}

}
