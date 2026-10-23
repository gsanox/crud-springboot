package com.saseformacion.crud.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Activa la caché de Spring. Sin @EnableCaching, las anotaciones @Cacheable,
 * @CachePut y @CacheEvict se ignoran sin dar ningún error.
 *
 * Al no haber ninguna librería de caché (Caffeine, Redis...), Spring Boot usa la más
 * sencilla: un Map en memoria (ConcurrentHashMap) por cada caché. Se vacía al reiniciar
 * la aplicación y no caduca nunca.
 */
@Configuration
@EnableCaching
public class CacheConfig {

	// Nombres de las cachés, en constantes para no escribirlos a mano en cada anotación
	public static final String PRODUCTO_POR_ID = "productoPorId";
	public static final String LISTA_PRODUCTOS = "listaProductos";

}
