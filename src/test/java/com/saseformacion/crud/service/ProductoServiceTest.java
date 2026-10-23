package com.saseformacion.crud.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.annotation.Transactional;

import com.saseformacion.crud.config.CacheConfig;
import com.saseformacion.crud.dto.ProductoRequest;
import com.saseformacion.crud.dto.ProductoResponse;
import com.saseformacion.crud.exception.ProductoDuplicadoException;
import com.saseformacion.crud.exception.ProductoNoEncontradoException;
import com.saseformacion.crud.exception.StockInsuficienteException;
import com.saseformacion.crud.repository.ProductoRepository;

// @Transactional deshace los cambios al terminar cada test: la base de datos queda como estaba
@SpringBootTest
@Transactional
class ProductoServiceTest {

	@Autowired
	private ProductoService productoService;

	@Autowired
	private ProductoRepository productoRepository;

	@Autowired
	private CacheManager cacheManager;

	@BeforeEach
	void empezarDeCero() {
		// La tabla se vacía dentro de la transacción del test (se deshace al terminar)
		productoRepository.deleteAll();
		// La caché NO es transaccional: hay que vaciarla a mano entre tests
		cacheManager.getCacheNames().forEach(nombre -> cacheManager.getCache(nombre).clear());
	}

	private ProductoRequest teclado() {
		return new ProductoRequest("Teclado", "Teclado mecánico", new BigDecimal("49.99"), 10);
	}

	// ---------- CRUD ----------

	@Test
	void crearYBuscarPorId() {
		ProductoResponse creado = productoService.crear(teclado());

		assertNotNull(creado.id());
		assertNotNull(creado.fechaCreacion());
		assertEquals("Teclado", productoService.buscarPorId(creado.id()).nombre());
	}

	@Test
	void actualizar() {
		ProductoResponse creado = productoService.crear(teclado());

		ProductoResponse actualizado = productoService.actualizar(creado.id(),
				new ProductoRequest("Teclado inalámbrico", null, new BigDecimal("59.90"), 3));

		assertEquals("Teclado inalámbrico", actualizado.nombre());
		assertEquals(3, actualizado.stock());
		assertEquals(creado.fechaCreacion(), actualizado.fechaCreacion());
	}

	@Test
	void eliminar() {
		ProductoResponse creado = productoService.crear(teclado());

		productoService.eliminar(creado.id());

		assertThrows(ProductoNoEncontradoException.class, () -> productoService.buscarPorId(creado.id()));
	}

	@Test
	void idInexistenteLanzaExcepcion() {
		assertThrows(ProductoNoEncontradoException.class, () -> productoService.buscarPorId(-1L));
		assertThrows(ProductoNoEncontradoException.class, () -> productoService.eliminar(-1L));
		assertThrows(ProductoNoEncontradoException.class, () -> productoService.actualizar(-1L, teclado()));
	}

	// ---------- reglas de negocio ----------

	@Test
	void noSePuedeCrearUnNombreRepetidoAunqueCambienMayusculas() {
		productoService.crear(teclado());

		assertThrows(ProductoDuplicadoException.class,
				() -> productoService.crear(new ProductoRequest("TECLADO", null, new BigDecimal("10.00"), 1)));
	}

	@Test
	void actualizarConservandoSuPropioNombreNoEsDuplicado() {
		ProductoResponse creado = productoService.crear(teclado());

		ProductoResponse actualizado = productoService.actualizar(creado.id(),
				new ProductoRequest("Teclado", "Otra descripción", new BigDecimal("49.99"), 10));

		assertEquals("Otra descripción", actualizado.descripcion());
	}

	@Test
	void venderRestaStock() {
		ProductoResponse creado = productoService.crear(teclado());

		ProductoResponse vendido = productoService.vender(creado.id(), 4);

		assertEquals(6, vendido.stock());
		assertTrue(vendido.disponible());
	}

	@Test
	void noSePuedeVenderMasDeLoQueHay() {
		ProductoResponse creado = productoService.crear(teclado());

		assertThrows(StockInsuficienteException.class, () -> productoService.vender(creado.id(), 11));
		assertEquals(10, productoService.buscarPorId(creado.id()).stock());
	}

	// ---------- caché ----------

	@Test
	void buscarPorIdGuardaElResultadoEnCache() {
		ProductoResponse creado = productoService.crear(teclado());
		Cache cache = cacheManager.getCache(CacheConfig.PRODUCTO_POR_ID);
		assertNull(cache.get(creado.id()));

		ProductoResponse primeraVez = productoService.buscarPorId(creado.id());
		ProductoResponse segundaVez = productoService.buscarPorId(creado.id());

		assertNotNull(cache.get(creado.id()));
		// La segunda vez no se consulta la base de datos: es literalmente el mismo objeto
		assertTrue(primeraVez == segundaVez);
	}

	@Test
	void actualizarDejaLaCacheAlDia() {
		ProductoResponse creado = productoService.crear(teclado());
		productoService.buscarPorId(creado.id());

		productoService.vender(creado.id(), 4);

		// @CachePut ha sustituido el valor: no se devuelve el stock antiguo
		assertEquals(6, productoService.buscarPorId(creado.id()).stock());
	}

	@Test
	void eliminarYCrearVacianLaCacheDeLaLista() {
		productoService.crear(teclado());
		assertEquals(1, productoService.listar().size());

		productoService.crear(new ProductoRequest("Ratón", null, new BigDecimal("19.90"), 5));
		assertEquals(2, productoService.listar().size());

		productoService.eliminar(productoService.listar().get(0).id());
		assertEquals(1, productoService.listar().size());
	}

	@Test
	void unErrorNoSeGuardaEnCache() {
		assertThrows(ProductoNoEncontradoException.class, () -> productoService.buscarPorId(-1L));

		assertNull(cacheManager.getCache(CacheConfig.PRODUCTO_POR_ID).get(-1L));
	}

}
