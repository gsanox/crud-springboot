package com.saseformacion.crud.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.saseformacion.crud.dto.ProductoRequest;
import com.saseformacion.crud.dto.ProductoResponse;
import com.saseformacion.crud.dto.VentaRequest;
import com.saseformacion.crud.service.ProductoService;

import jakarta.validation.Valid;

/**
 * Solo traduce HTTP ↔ Java: recibe DTOs, llama al servicio y devuelve DTOs.
 * No tiene try/catch: los errores los atrapa ManejadorGlobalExcepciones.
 */
@RestController
@RequestMapping("/api/productos")
public class ProductoController {

	private final ProductoService productoService;

	public ProductoController(ProductoService productoService) {
		this.productoService = productoService;
	}

	// GET /api/productos
	@GetMapping
	public List<ProductoResponse> listar() {
		return productoService.listar();
	}

	// GET /api/productos/1
	@GetMapping("/{id}")
	public ProductoResponse buscarPorId(@PathVariable Long id) {
		return productoService.buscarPorId(id);
	}

	// POST /api/productos  (el producto va en el cuerpo de la petición, en JSON)
	// @Valid comprueba las anotaciones de ProductoRequest antes de entrar en el método
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProductoResponse crear(@Valid @RequestBody ProductoRequest request) {
		return productoService.crear(request);
	}

	// PUT /api/productos/1
	@PutMapping("/{id}")
	public ProductoResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProductoRequest request) {
		return productoService.actualizar(id, request);
	}

	// DELETE /api/productos/1
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminar(@PathVariable Long id) {
		productoService.eliminar(id);
	}

	// POST /api/productos/1/ventas  con {"cantidad": 3}: resta 3 unidades del stock
	@PostMapping("/{id}/ventas")
	public ProductoResponse vender(@PathVariable Long id, @Valid @RequestBody VentaRequest request) {
		return productoService.vender(id, request.cantidad());
	}

}
