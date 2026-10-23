package com.saseformacion.crud.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saseformacion.crud.config.CacheConfig;
import com.saseformacion.crud.dto.ProductoRequest;
import com.saseformacion.crud.dto.ProductoResponse;
import com.saseformacion.crud.exception.ProductoDuplicadoException;
import com.saseformacion.crud.exception.ProductoNoEncontradoException;
import com.saseformacion.crud.exception.StockInsuficienteException;
import com.saseformacion.crud.mapper.ProductoMapper;
import com.saseformacion.crud.model.Producto;
import com.saseformacion.crud.repository.ProductoRepository;

/**
 * Lógica de negocio de los productos.
 * - Recibe y devuelve DTOs: la entidad Producto no sale de esta capa.
 * - Aplica las reglas del negocio y, si no se cumplen, lanza una excepción con significado.
 * - No sabe nada de HTTP: los códigos de estado los decide ManejadorGlobalExcepciones.
 */
@Service
public class ProductoService {

	private static final Logger log = LoggerFactory.getLogger(ProductoService.class);

	private final ProductoRepository productoRepository;
	private final ProductoMapper productoMapper;

	// Inyección por constructor: Spring pasa el repositorio y el mapper automáticamente
	public ProductoService(ProductoRepository productoRepository, ProductoMapper productoMapper) {
		this.productoRepository = productoRepository;
		this.productoMapper = productoMapper;
	}

	// La primera vez consulta la base de datos; las siguientes devuelve la lista guardada en caché
	@Cacheable(CacheConfig.LISTA_PRODUCTOS)
	@Transactional(readOnly = true)
	public List<ProductoResponse> listar() {
		log.info("Consultando la base de datos: todos los productos");
		return productoMapper.toResponseList(productoRepository.findAll());
	}

	// Se guarda en caché con el id como clave. Si lanza una excepción, no se guarda nada.
	@Cacheable(value = CacheConfig.PRODUCTO_POR_ID, key = "#id")
	@Transactional(readOnly = true)
	public ProductoResponse buscarPorId(Long id) {
		log.info("Consultando la base de datos: producto {}", id);
		return productoMapper.toResponse(obtenerProducto(id));
	}

	// Un producto nuevo cambia la lista: se vacía esa caché
	@CacheEvict(value = CacheConfig.LISTA_PRODUCTOS, allEntries = true)
	@Transactional
	public ProductoResponse crear(ProductoRequest request) {
		// Regla de negocio: no puede haber dos productos con el mismo nombre
		if (productoRepository.existsByNombreIgnoreCase(request.nombre())) {
			throw new ProductoDuplicadoException(request.nombre());
		}

		Producto producto = productoMapper.toEntity(request);
		// Datos que decide el servidor, nunca el cliente
		producto.setFechaCreacion(LocalDateTime.now());

		return productoMapper.toResponse(productoRepository.save(producto));
	}

	// @CachePut ejecuta siempre el método y guarda el resultado en la caché (la deja actualizada)
	@Caching(
			put = @CachePut(value = CacheConfig.PRODUCTO_POR_ID, key = "#id"),
			evict = @CacheEvict(value = CacheConfig.LISTA_PRODUCTOS, allEntries = true))
	@Transactional
	public ProductoResponse actualizar(Long id, ProductoRequest request) {
		Producto producto = obtenerProducto(id);

		// Mismo nombre que OTRO producto → duplicado (el suyo propio sí se puede repetir)
		if (productoRepository.existsByNombreIgnoreCaseAndIdNot(request.nombre(), id)) {
			throw new ProductoDuplicadoException(request.nombre());
		}

		productoMapper.updateEntity(producto, request);
		return productoMapper.toResponse(productoRepository.save(producto));
	}

	@Caching(evict = {
			@CacheEvict(value = CacheConfig.PRODUCTO_POR_ID, key = "#id"),
			@CacheEvict(value = CacheConfig.LISTA_PRODUCTOS, allEntries = true) })
	@Transactional
	public void eliminar(Long id) {
		if (!productoRepository.existsById(id)) {
			throw new ProductoNoEncontradoException(id);
		}
		productoRepository.deleteById(id);
	}

	@Caching(
			put = @CachePut(value = CacheConfig.PRODUCTO_POR_ID, key = "#id"),
			evict = @CacheEvict(value = CacheConfig.LISTA_PRODUCTOS, allEntries = true))
	@Transactional
	public ProductoResponse vender(Long id, int cantidad) {
		Producto producto = obtenerProducto(id);

		// Regla de negocio: no se puede vender más de lo que hay
		if (producto.getStock() < cantidad) {
			throw new StockInsuficienteException(producto.getNombre(), producto.getStock(), cantidad);
		}

		producto.setStock(producto.getStock() - cantidad);
		return productoMapper.toResponse(productoRepository.save(producto));
	}

	// Método privado de apoyo: devuelve la ENTIDAD (para modificarla) o lanza la excepción.
	// Es privado, así que la entidad nunca sale del servicio.
	private Producto obtenerProducto(Long id) {
		return productoRepository.findById(id)
				.orElseThrow(() -> new ProductoNoEncontradoException(id));
	}

}
