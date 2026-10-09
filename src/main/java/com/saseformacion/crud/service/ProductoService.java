package com.saseformacion.crud.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saseformacion.crud.exception.ProductoNoEncontradoException;
import com.saseformacion.crud.model.Producto;
import com.saseformacion.crud.repository.ProductoRepository;

@Service
public class ProductoService {

	private final ProductoRepository productoRepository;

	// Inyección por constructor: Spring pasa el repositorio automáticamente
	public ProductoService(ProductoRepository productoRepository) {
		this.productoRepository = productoRepository;
	}

	@Transactional(readOnly = true)
	public List<Producto> listar() {
		return productoRepository.findAll();
	}

	@Transactional(readOnly = true)
	public Producto buscarPorId(Long id) {
		return productoRepository.findById(id)
				.orElseThrow(() -> new ProductoNoEncontradoException(id));
	}

	@Transactional
	public Producto crear(Producto producto) {
		// El id lo genera la base de datos; si viene informado se ignora
		producto.setId(null);
		return productoRepository.save(producto);
	}

	@Transactional
	public Producto actualizar(Long id, Producto datos) {
		Producto producto = buscarPorId(id);
		producto.setNombre(datos.getNombre());
		producto.setDescripcion(datos.getDescripcion());
		producto.setPrecio(datos.getPrecio());
		producto.setStock(datos.getStock());
		return productoRepository.save(producto);
	}

	@Transactional
	public void eliminar(Long id) {
		if (!productoRepository.existsById(id)) {
			throw new ProductoNoEncontradoException(id);
		}
		productoRepository.deleteById(id);
	}

}
