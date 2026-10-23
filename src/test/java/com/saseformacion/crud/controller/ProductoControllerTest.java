package com.saseformacion.crud.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.saseformacion.crud.dto.ProductoRequest;
import com.saseformacion.crud.dto.ProductoResponse;
import com.saseformacion.crud.exception.ProductoDuplicadoException;
import com.saseformacion.crud.exception.ProductoNoEncontradoException;
import com.saseformacion.crud.exception.StockInsuficienteException;
import com.saseformacion.crud.service.ProductoService;

// @WebMvcTest solo carga el controlador y los @RestControllerAdvice (ManejadorGlobalExcepciones);
// el servicio se sustituye por un mock, así que no hace falta base de datos
@WebMvcTest(ProductoController.class)
class ProductoControllerTest {

	private static final String TECLADO_JSON = """
			{"nombre": "Teclado", "descripcion": "Teclado mecánico", "precio": 49.99, "stock": 10}
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProductoService productoService;

	private ProductoResponse teclado() {
		return new ProductoResponse(1L, "Teclado", "Teclado mecánico", new BigDecimal("49.99"), 10, true,
				LocalDateTime.of(2026, 10, 23, 10, 0));
	}

	// ---------- casos correctos ----------

	@Test
	void listar() throws Exception {
		when(productoService.listar()).thenReturn(List.of(teclado()));

		mockMvc.perform(get("/api/productos"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].nombre").value("Teclado"))
				.andExpect(jsonPath("$[0].disponible").value(true));
	}

	@Test
	void buscarPorId() throws Exception {
		when(productoService.buscarPorId(1L)).thenReturn(teclado());

		mockMvc.perform(get("/api/productos/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.precio").value(49.99))
				.andExpect(jsonPath("$.fechaCreacion").value("2026-10-23T10:00:00"));
	}

	@Test
	void crearDevuelve201() throws Exception {
		when(productoService.crear(any(ProductoRequest.class))).thenReturn(teclado());

		mockMvc.perform(post("/api/productos").contentType(MediaType.APPLICATION_JSON).content(TECLADO_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(1));
	}

	@Test
	void actualizar() throws Exception {
		when(productoService.actualizar(eq(1L), any(ProductoRequest.class))).thenReturn(teclado());

		mockMvc.perform(put("/api/productos/1").contentType(MediaType.APPLICATION_JSON).content(TECLADO_JSON))
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
	void vender() throws Exception {
		when(productoService.vender(1L, 3)).thenReturn(teclado());

		mockMvc.perform(post("/api/productos/1/ventas").contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\": 3}"))
				.andExpect(status().isOk());

		verify(productoService).vender(1L, 3);
	}

	// ---------- errores (ManejadorGlobalExcepciones) ----------

	@Test
	void noEncontradoDevuelve404ConFormatoComun() throws Exception {
		when(productoService.buscarPorId(99L)).thenThrow(new ProductoNoEncontradoException(99L));

		mockMvc.perform(get("/api/productos/99"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.estado").value(404))
				.andExpect(jsonPath("$.mensaje").value("No existe ningún producto con id 99"))
				.andExpect(jsonPath("$.ruta").value("/api/productos/99"));
	}

	@Test
	void eliminarInexistenteDevuelve404() throws Exception {
		doThrow(new ProductoNoEncontradoException(99L)).when(productoService).eliminar(99L);

		mockMvc.perform(delete("/api/productos/99"))
				.andExpect(status().isNotFound());
	}

	@Test
	void datosNoValidosDevuelven400SinLlegarAlServicio() throws Exception {
		mockMvc.perform(post("/api/productos").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nombre": "", "precio": -5, "stock": 10}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.nombre").value("El nombre es obligatorio"))
				.andExpect(jsonPath("$.errores.precio").value("El precio tiene que ser mayor que 0"));

		verifyNoInteractions(productoService);
	}

	@Test
	void jsonMalFormadoDevuelve400() throws Exception {
		mockMvc.perform(post("/api/productos").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\": "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("El cuerpo de la petición no es un JSON válido o algún campo tiene un tipo incorrecto"));
	}

	@Test
	void idNoNumericoDevuelve400() throws Exception {
		mockMvc.perform(get("/api/productos/abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("El valor 'abc' no es válido para el parámetro 'id'"));
	}

	@Test
	void duplicadoDevuelve409() throws Exception {
		when(productoService.crear(any(ProductoRequest.class))).thenThrow(new ProductoDuplicadoException("Teclado"));

		mockMvc.perform(post("/api/productos").contentType(MediaType.APPLICATION_JSON).content(TECLADO_JSON))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensaje").value("Ya existe un producto con el nombre 'Teclado'"));
	}

	@Test
	void stockInsuficienteDevuelve409() throws Exception {
		when(productoService.vender(1L, 50)).thenThrow(new StockInsuficienteException("Teclado", 10, 50));

		mockMvc.perform(post("/api/productos/1/ventas").contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\": 50}"))
				.andExpect(status().isConflict());
	}

	@Test
	void verboNoPermitidoMantiene405() throws Exception {
		mockMvc.perform(delete("/api/productos"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.estado").value(405));
	}

	@Test
	void errorInesperadoDevuelve500SinDetallesInternos() throws Exception {
		when(productoService.listar()).thenThrow(new IllegalStateException("conexión perdida con la BD"));

		mockMvc.perform(get("/api/productos"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.mensaje").value("Ha ocurrido un error inesperado"));
	}

}
