# Lección 1 · La capa de servicio

[← Índice](README.md) · [Siguiente: Patrón DTO →](02-patron-dto.md)

## Qué vas a aprender

- Qué es exactamente la **lógica de negocio** y por qué va en el servicio.
- Cómo se escribe una **regla de negocio** y qué hacer cuando no se cumple.
- Para qué sirve `@Transactional` y por qué importa que nuestras excepciones sean `RuntimeException`.
- Cómo pedirle al repositorio consultas nuevas **sin escribir SQL**.

## El papel del servicio, en una frase

> El controlador habla **HTTP**, el repositorio habla **SQL**, y el servicio habla **el idioma del negocio**: productos, ventas, stock.

En la versión básica el servicio casi no hacía nada: pasaba las llamadas del controlador al repositorio. Eso es normal al empezar, pero un servicio de verdad es donde viven las **reglas**: lo que la tienda permite y lo que no.

## Las reglas de negocio de este proyecto

| Regla | Dónde | Si no se cumple |
|---|---|---|
| El `id` y la `fechaCreacion` los pone el servidor | `crear()` | (no hay error: se ignora lo que mande el cliente) |
| No puede haber dos productos con el mismo nombre (sin distinguir mayúsculas) | `crear()` y `actualizar()` | `ProductoDuplicadoException` |
| No se pueden vender más unidades de las que hay | `vender()` | `StockInsuficienteException` |
| No se puede leer, modificar ni borrar un producto que no existe | casi todos | `ProductoNoEncontradoException` |

Así queda `vender()` en `service/ProductoService.java` (sin las anotaciones de caché, que verás en la lección 5):

```java
@Transactional
public ProductoResponse vender(Long id, int cantidad) {
    Producto producto = obtenerProducto(id);            // ← 404 si no existe

    // Regla de negocio: no se puede vender más de lo que hay
    if (producto.getStock() < cantidad) {
        throw new StockInsuficienteException(producto.getNombre(), producto.getStock(), cantidad);
    }

    producto.setStock(producto.getStock() - cantidad);
    return productoMapper.toResponse(productoRepository.save(producto));
}
```

Fíjate en tres cosas:

1. **El servicio no sabe nada de HTTP.** No devuelve un 409 ni un `ResponseEntity`: lanza una excepción **con significado** (`StockInsuficienteException`). Quién decide que eso es un 409 lo verás en la [lección 4](04-excepciones.md).
2. **El método se lee como el enunciado del problema**: busca el producto, comprueba que hay stock, resta, guarda. Por eso las reglas van aquí y no repartidas por el controlador.
3. **Un método nuevo (`vender`) no es un CRUD.** Un servicio no tiene por qué limitarse a crear/leer/actualizar/borrar: refleja las operaciones del negocio.

### ¿Por qué no comprobar el stock en el controlador?

Porque mañana puede haber otra forma de vender (una tarea programada, otro controlador, un mensaje de una cola…). Si la regla está en el servicio, **todas pasan por ella**. Si está en un controlador, la otra vía se la salta.

## Lanzar excepciones en vez de devolver `null` o `false`

Una alternativa sería que `vender()` devolviera `false` si no hay stock, o que `buscarPorId()` devolviera `null` si no existe. El problema es que **quien llama puede olvidarse de comprobarlo**, y el error aparece mucho más tarde y lejos (el famoso `NullPointerException`).

Una excepción no se puede ignorar sin querer: o alguien la atrapa, o la petición termina en error. Y el nombre de la clase ya explica qué ha pasado.

Las tres excepciones del proyecto heredan de `RuntimeException` (excepciones **no comprobadas**), así que no obligan a poner `throws` ni `try/catch` en cada método por el que pasan:

```java
public class ProductoDuplicadoException extends RuntimeException {

    public ProductoDuplicadoException(String nombre) {
        super("Ya existe un producto con el nombre '" + nombre + "'");
    }
}
```

## El método privado `obtenerProducto()`

Varios métodos necesitan «búscame el producto o lanza un 404». En vez de repetirlo, hay un método privado:

```java
// Devuelve la ENTIDAD (para modificarla) o lanza la excepción.
// Es privado, así que la entidad nunca sale del servicio.
private Producto obtenerProducto(Long id) {
    return productoRepository.findById(id)
            .orElseThrow(() -> new ProductoNoEncontradoException(id));
}
```

Los métodos públicos devuelven DTOs (`ProductoResponse`, [lección 2](02-patron-dto.md)); este, al ser privado, puede devolver la entidad para que `actualizar()` y `vender()` la modifiquen.

## `@Transactional`: todo o nada

Cada método público lleva `@Transactional`. Significa que todo lo que hace en la base de datos va en **una transacción**: si el método termina bien, se confirman los cambios (*commit*); si lanza una excepción, se deshacen (*rollback*).

- En `actualizar()` hay un `SELECT` (buscar) y un `UPDATE` (guardar). Con la transacción, nadie ve un estado a medias.
- Las lecturas llevan `@Transactional(readOnly = true)`: avisa de que no se va a escribir, y la base de datos puede optimizar.

> ⚠️ **Detalle importante:** por defecto Spring solo hace *rollback* con excepciones **no comprobadas** (`RuntimeException` y sus hijas). Con una excepción comprobada (`extends Exception`) haría *commit* aunque falle. Otra razón para que nuestras excepciones de negocio hereden de `RuntimeException`.

## Consultas derivadas: el repositorio aprende cosas nuevas

Para la regla del nombre único hace falta preguntar «¿existe un producto con este nombre?». No hay que escribir SQL: basta con **declarar un método con un nombre que siga las reglas de Spring Data**.

```java
// repository/ProductoRepository.java
boolean existsByNombreIgnoreCase(String nombre);
boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
```

Spring Data lee el nombre por partes y genera la consulta al arrancar:

| Trozo del nombre | Significa |
|---|---|
| `existsBy` | devuelve `true`/`false` (hay también `findBy`, `countBy`, `deleteBy`) |
| `Nombre` | filtra por el atributo `nombre` de la entidad |
| `IgnoreCase` | sin distinguir mayúsculas: `upper(nombre) = upper(?)` |
| `And` | y además… |
| `IdNot` | …el `id` es distinto de este (`id <> ?`) |

¿Por qué hay dos? Al **actualizar**, el producto ya tiene su nombre guardado. Si usáramos la primera, al actualizar el «Teclado» sin cambiarle el nombre, encontraría… a sí mismo, y diría que está duplicado. Por eso se excluye su propio `id`.

> Si escribes mal un atributo (`existsByNonbre`), la aplicación **no arranca**: Spring Data comprueba los nombres al iniciar. Mejor eso que un error en producción.

## ¿Y lo de `ProductoService` + `ProductoServiceImpl`?

En muchos cursos y proyectos verás el servicio dividido en una **interfaz** y una **implementación**:

```java
public interface ProductoService {
    ProductoResponse buscarPorId(Long id);
    // ...
}

@Service
public class ProductoServiceImpl implements ProductoService {
    // el código de verdad
}
```

El controlador depende de la interfaz, y se podría cambiar la implementación sin tocarlo. Es útil cuando hay **varias** implementaciones de verdad (por ejemplo, `PagoConTarjeta` y `PagoConBizum`). Con una sola, es más código sin beneficio claro, y Spring y Mockito funcionan igual de bien con clases. En este proyecto se ha dejado una sola clase para que se lea mejor, pero si en clase usáis el patrón con interfaz, es un cambio mecánico y no altera nada de lo que se explica aquí.

## Cómo comprobarlo

En [`peticiones.http`](../peticiones.http), sección *Lección 1*:

- Crea un producto y vuelve a crearlo con el nombre en mayúsculas → **409** «Ya existe un producto…».
- Vende 4 unidades → **200** con el stock restado. Vende 100 → **409** «Stock insuficiente…».

Los tests de las reglas están en `ProductoServiceTest` (`noSePuedeCrearUnNombreRepetidoAunqueCambienMayusculas`, `noSePuedeVenderMasDeLoQueHay`…):

```bash
./mvnw test -Dtest=ProductoServiceTest
```

## Errores típicos

- **Poner reglas en el controlador** («si el precio es negativo devuelvo 400» con un `if` en el controlador). La validación del *formato* de los datos va en el DTO (lección 2); las reglas del *negocio*, en el servicio.
- **Devolver `null`** cuando algo no existe, en vez de lanzar una excepción.
- **Llamar a un repositorio desde el controlador** «porque es solo un `findAll`». Hoy es solo un `findAll`; mañana necesita una regla y ya no hay un sitio único donde ponerla.
- **Olvidar `@Transactional`** en un método que hace varias escrituras: si falla la segunda, la primera se queda hecha.

## Preguntas de repaso

1. ¿Por qué `vender()` lanza una excepción en vez de devolver `false` si no hay stock?
2. Al actualizar el producto 5 sin cambiarle el nombre, ¿qué pasaría si el servicio usara `existsByNombreIgnoreCase` en vez de `existsByNombreIgnoreCaseAndIdNot`?
3. Si `ProductoDuplicadoException` heredara de `Exception` en vez de `RuntimeException`, ¿qué dos cosas cambiarían?
4. ¿Dónde pondrías una regla nueva: «no se puede borrar un producto que todavía tiene stock»? ¿Qué excepción lanzarías?

<details>
<summary>Ver respuestas</summary>

1. Porque un `false` se puede ignorar sin querer y el error pasaría desapercibido. Una excepción obliga a tratar el caso, explica qué ha pasado con su nombre, y además provoca el *rollback* de la transacción.
2. Encontraría al propio producto 5 y lanzaría `ProductoDuplicadoException`: no se podría actualizar ningún producto sin cambiarle el nombre.
3. Habría que declararla con `throws` (o capturarla) en todos los métodos por los que pasa, y `@Transactional` ya no haría *rollback* automático al lanzarla.
4. En `eliminar()` del servicio, antes de `deleteById`: `if (producto.getStock() > 0) throw new ...`. Lo natural sería una excepción nueva (por ejemplo `ProductoConStockException`) que el manejador global convirtiera en un 409.

</details>
