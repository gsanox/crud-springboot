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

import com.saseformacion.crud.model.Producto;
import com.saseformacion.crud.service.ProductoService;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

	private final ProductoService productoService;

	public ProductoController(ProductoService productoService) {
		this.productoService = productoService;
	}

	// GET /api/productos
	@GetMapping
	public List<Producto> listar() {
		return productoService.listar();
	}

	// GET /api/productos/1
	@GetMapping("/{id}")
	public Producto buscarPorId(@PathVariable Long id) {
		return productoService.buscarPorId(id);
	}

	// POST /api/productos  (el producto va en el cuerpo de la petición, en JSON)
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Producto crear(@RequestBody Producto producto) {
		return productoService.crear(producto);
	}

	// PUT /api/productos/1
	@PutMapping("/{id}")
	public Producto actualizar(@PathVariable Long id, @RequestBody Producto producto) {
		return productoService.actualizar(id, producto);
	}

	// DELETE /api/productos/1
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminar(@PathVariable Long id) {
		productoService.eliminar(id);
	}

}
