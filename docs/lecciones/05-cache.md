# Lección 5 · Caché

[← Excepciones](04-excepciones.md) · [Índice](README.md)

## Qué vas a aprender

- Qué es una **caché** y cuándo compensa usarla.
- Cómo se activa en Spring y qué hacen **`@Cacheable`**, **`@CachePut`** y **`@CacheEvict`**.
- El problema de verdad de las cachés: **mantener los datos al día**.
- Las trampas típicas: llamadas internas, cambios hechos por fuera de la aplicación y los tests.

## Qué es una caché

Una caché es una **memoria rápida donde se guardan resultados ya calculados** para no tener que calcularlos otra vez.

Pedir un producto a PostgreSQL implica una conexión de red, una consulta SQL, leer del disco y convertir la fila en un objeto. Si el mismo producto se pide mil veces por minuto y casi nunca cambia, la caché lo devuelve directamente desde la memoria de la aplicación:

```text
1ª petición   GET /api/productos/7 ─► ¿está en la caché? NO ─► PostgreSQL ─► se guarda en la caché ─► respuesta
2ª petición   GET /api/productos/7 ─► ¿está en la caché? SÍ ───────────────────────────────────────► respuesta
```

**Compensa** cuando los datos se leen mucho más de lo que cambian (catálogos, configuración, listas de países…). **No compensa** con datos que cambian a cada momento, o que tienen que estar exactos al segundo (el saldo de una cuenta bancaria).

## 1. Activarla

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```

```java
// config/CacheConfig.java
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String PRODUCTO_POR_ID = "productoPorId";
    public static final String LISTA_PRODUCTOS = "listaProductos";
}
```

- Sin **`@EnableCaching`**, las anotaciones de caché **se ignoran sin dar ningún error**. Es el fallo más común.
- Los nombres de las cachés van en constantes para no escribirlos a mano (y con faltas) en cada anotación.
- En `application.properties`, `spring.cache.type=simple` indica la caché más sencilla: un **`Map` en memoria** por cada caché. Se vacía al reiniciar la aplicación y sus datos no caducan nunca.

Cada caché es como un `Map<clave, valor>`:

```text
productoPorId    →   { 7 → ProductoResponse(id=7, "Teclado"...),  12 → ProductoResponse(id=12, ...) }
listaProductos   →   { SimpleKey[] → [ProductoResponse(...), ProductoResponse(...)] }
```

## 2. Las tres anotaciones

| Anotación | ¿Ejecuta el método? | Qué hace con la caché | Se usa para |
|---|---|---|---|
| `@Cacheable` | Solo si el dato **no** está en la caché | Guarda el resultado | Leer |
| `@CachePut` | **Siempre** | Guarda (sustituye) el resultado | Actualizar |
| `@CacheEvict` | **Siempre** | Borra una entrada, o todas con `allEntries = true` | Borrar o invalidar |

`@Caching` permite poner varias a la vez en un mismo método.

### Leer: `@Cacheable`

```java
// service/ProductoService.java
@Cacheable(value = CacheConfig.PRODUCTO_POR_ID, key = "#id")
@Transactional(readOnly = true)
public ProductoResponse buscarPorId(Long id) {
    log.info("Consultando la base de datos: producto {}", id);
    return productoMapper.toResponse(obtenerProducto(id));
}
```

- `key = "#id"` usa el parámetro `id` como clave (es una expresión SpEL: `#` + nombre del parámetro).
- Si el método **lanza una excepción**, no se guarda nada: un 404 no se queda «pegado» en la caché.
- El `log.info` sirve para **ver la caché funcionando**: solo aparece en la consola cuando de verdad se consulta la base de datos.

### Modificar: mantener la caché al día

Este es el problema de verdad de cualquier caché: **si el dato cambia en la base de datos y la caché no se entera, se devuelven datos viejos**. Cada método que modifica algo tiene que decir qué pasa con la caché:

| Método | `productoPorId` | `listaProductos` | Por qué |
|---|---|---|---|
| `listar()` | – | `@Cacheable` | |
| `buscarPorId(id)` | `@Cacheable` | – | |
| `crear(...)` | – | `@CacheEvict(allEntries)` | La lista ya no está completa |
| `actualizar(id, ...)` | `@CachePut` | `@CacheEvict(allEntries)` | El producto cambia, y la lista también |
| `vender(id, ...)` | `@CachePut` | `@CacheEvict(allEntries)` | Cambia el stock |
| `eliminar(id)` | `@CacheEvict` | `@CacheEvict(allEntries)` | Ya no existe |

```java
@Caching(
        put = @CachePut(value = CacheConfig.PRODUCTO_POR_ID, key = "#id"),
        evict = @CacheEvict(value = CacheConfig.LISTA_PRODUCTOS, allEntries = true))
@Transactional
public ProductoResponse actualizar(Long id, ProductoRequest request) { ... }
```

¿Por qué `@CachePut` al actualizar y no `@CacheEvict`? Las dos opciones son correctas. `@CachePut` guarda directamente el producto ya actualizado, así que la siguiente lectura ni siquiera va a la base de datos. `@CacheEvict` lo borraría, y la siguiente lectura lo volvería a cargar.

¿Por qué la lista se vacía entera? Porque saber qué posición de la lista ocupa cada producto es complicado. Vaciarla es sencillo y seguro: la siguiente llamada a `listar()` la reconstruye.

## 3. Por qué se guardan DTOs y no entidades

La caché guarda **el objeto tal cual** en memoria, y todas las peticiones reciben **el mismo objeto**. Si fuera una entidad con *setters*, cualquier código que la modificara cambiaría «sin querer» lo que hay en la caché. Además, una entidad JPA está ligada a la sesión de Hibernate que la cargó.

Los DTOs son `record` (**inmutables**) y la lista viene de `.toList()` (**no modificable**), así que nadie puede estropear lo que hay en la caché. Otra ventaja de la [lección 2](02-patron-dto.md).

## 4. Verlo funcionar

Arranca la aplicación y usa la sección *Lección 5* de [`peticiones.http`](../peticiones.http), mirando la consola:

```text
GET /api/productos/7   →  Consultando la base de datos: producto 7     ← 1ª vez: va a PostgreSQL (y sale el SQL)
GET /api/productos/7   →  (nada)                                        ← de la caché
GET /api/productos/7   →  (nada)                                        ← de la caché
POST /api/productos/7/ventas  {"cantidad": 4}                           ← @CachePut: la caché se actualiza
GET /api/productos/7   →  (nada), y el stock ya sale restado            ← de la caché, pero al día
DELETE /api/productos/7                                                 ← @CacheEvict
GET /api/productos/7   →  Consultando la base de datos: producto 7  → 404
```

### Experimento: cambiar la base de datos «por detrás»

1. Pide `GET /api/productos/7` (queda en la caché).
2. Cambia el precio **directamente en PostgreSQL**, sin pasar por la API:
   ```bash
   docker exec -it crud-postgres psql -U postgres -d crud -c "update productos set precio = 1 where id = 7"
   ```
3. Vuelve a pedir `GET /api/productos/7`: **sigue saliendo el precio antiguo**.

La caché solo se entera de los cambios que pasan **por los métodos anotados**. Cualquier cambio por otra vía (otro programa, un script SQL, otra instancia de la aplicación) la deja desactualizada hasta que se reinicie la aplicación. Por eso las cachés de verdad tienen **caducidad** (ver el final de la lección).

## 5. Trampas típicas

### La llamada interna no pasa por la caché

Spring implementa la caché con un **proxy**: un objeto que envuelve al servicio y mira la caché antes de llamar al método de verdad. Eso solo funciona cuando la llamada **viene de fuera** (del controlador):

```text
Controller ──► [proxy: mira la caché] ──► ProductoService.buscarPorId()      ✔ usa la caché
ProductoService.otroMetodo() ──► this.buscarPorId()                         ✘ no pasa por el proxy
```

Si un método del servicio llama a `this.buscarPorId(id)`, la caché **se ignora**. Lo mismo pasa con `@Transactional`. En este proyecto no es un problema porque los métodos internos usan `obtenerProducto()`, que no tiene caché a propósito. Pero es un error clásico cuando «la caché no funciona».

### Los tests y la caché

La caché **no es transaccional**: aunque un test con `@Transactional` deshaga los cambios en la base de datos, lo que se guardó en la caché se queda ahí para el siguiente test. Por eso `ProductoServiceTest` la vacía antes de cada test:

```java
@BeforeEach
void empezarDeCero() {
    productoRepository.deleteAll();
    // La caché NO es transaccional: hay que vaciarla a mano entre tests
    cacheManager.getCacheNames().forEach(nombre -> cacheManager.getCache(nombre).clear());
}
```

Y los tests de caché comprueban directamente su contenido con `CacheManager`:

```java
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
```

```bash
./mvnw test -Dtest=ProductoServiceTest
```

## 6. En un proyecto real

La caché `simple` es perfecta para aprender, pero tiene límites: no caduca, no tiene tamaño máximo (puede llenar la memoria), y si la aplicación se ejecuta en varios servidores, **cada uno tiene su propia caché** y pueden no coincidir.

| Caché | Dónde vive | Caducidad / tamaño máximo | Compartida entre servidores |
|---|---|---|---|
| `simple` (este proyecto) | memoria de la aplicación | No | No |
| **Caffeine** | memoria de la aplicación | Sí (`expireAfterWrite`, `maximumSize`) | No |
| **Redis** | servidor aparte | Sí (TTL) | Sí |

Lo bueno: **las anotaciones no cambian**. Pasar a Caffeine o Redis es añadir la dependencia y configurarlo; `@Cacheable`, `@CachePut` y `@CacheEvict` siguen igual. Esa es la ventaja de que Spring separe la «abstracción de caché» de la implementación.

## Errores típicos

- **Olvidar `@EnableCaching`**: nada falla, pero no se cachea nada.
- **Cachear y no invalidar**: añadir `@Cacheable` a una lectura sin poner `@CacheEvict`/`@CachePut` en las escrituras. Se devuelven datos viejos.
- **Llamar al método cacheado desde la misma clase** (`this.buscarPorId()`): se salta la caché.
- **Cachear entidades** o listas modificables.
- **Cachear todo «por si acaso»**: una caché añade complejidad y riesgo de datos viejos. Solo donde se lee mucho y cambia poco.

## Preguntas de repaso

1. Si `crear()` no tuviera `@CacheEvict` sobre `listaProductos`, ¿qué vería un cliente que pide la lista justo después de crear un producto?
2. ¿Qué diferencia hay entre `@Cacheable` y `@CachePut`? ¿Por qué `vender()` usa la segunda?
3. Pides el producto 7, lo borras con un `DELETE` desde `psql` y vuelves a pedirlo por la API. ¿Qué recibes? ¿Y si lo borras con `DELETE /api/productos/7`?
4. ¿Por qué `ProductoServiceTest` vacía la caché antes de cada test si los tests ya llevan `@Transactional`?

<details>
<summary>Ver respuestas</summary>

1. La lista antigua, sin el producto nuevo, hasta que se reiniciara la aplicación: `listar()` seguiría devolviendo lo que tenía guardado.
2. `@Cacheable` solo ejecuta el método si el dato no está en la caché (para leer). `@CachePut` lo ejecuta siempre y guarda el resultado. `vender()` modifica el producto, así que tiene que ejecutarse siempre y dejar el valor nuevo en la caché.
3. Desde `psql`: un **200** con el producto, porque sale de la caché y la caché no se ha enterado del borrado. Con la API: un **404**, porque `eliminar()` tiene `@CacheEvict` y la siguiente lectura va a la base de datos.
4. Porque `@Transactional` solo deshace los cambios **en la base de datos**. La caché está en memoria y no forma parte de la transacción, así que conservaría datos de tests anteriores.

</details>
