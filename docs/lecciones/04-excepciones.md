# Lección 4 · Tratamiento de excepciones

[← Mappers](03-mappers.md) · [Índice](README.md) · [Siguiente: Caché →](05-cache.md)

## Qué vas a aprender

- Cómo **viaja una excepción** desde el servicio hasta convertirse en una respuesta HTTP.
- Cómo centralizar todos los errores en una clase con **`@RestControllerAdvice`** y **`@ExceptionHandler`**.
- Qué código HTTP corresponde a cada tipo de error.
- Por qué el cliente nunca debe ver trazas de Java ni detalles internos.

## El problema: cada error, de su padre y de su madre

En la versión básica, `ProductoNoEncontradoException` llevaba `@ResponseStatus(HttpStatus.NOT_FOUND)`. Funcionaba para ese caso, pero:

- El JSON de error lo generaba Spring a su manera, y con DevTools incluía **la traza completa de Java**.
- Un nombre vacío o un precio negativo acababa en un **500** (fallaba PostgreSQL), como si fuera culpa del servidor.
- Un JSON mal escrito, un `/api/productos/abc`… cada uno salía con un formato distinto.

Para quien programa el cliente (una web, una app móvil), eso es una pesadilla: no sabe qué formato va a recibir.

## La idea: el servicio lanza, un único sitio traduce

```text
ProductoService                 ProductoController             ManejadorGlobalExcepciones          Cliente
───────────────                 ──────────────────             ──────────────────────────          ───────
throw new                       (no la captura:                @ExceptionHandler(                  404
ProductoNoEncontradoException ─► la deja pasar)  ──────────────► ProductoNoEncontradoException) ──► {"estado": 404,
                                                                → ResponseEntity 404 + JSON          "mensaje": "No existe..."}
```

1. El **servicio** detecta el problema y lanza una excepción **con significado**. No sabe nada de HTTP.
2. La excepción **sube** por la pila de llamadas: nadie la captura con `try/catch`, así que atraviesa el controlador.
3. Antes de que llegue a Tomcat, Spring busca un **`@ExceptionHandler`** que sepa tratarla.
4. El *handler* decide el **código HTTP** y construye el **JSON de error**, siempre con el mismo formato.

Así **ningún controlador tiene `try/catch`**, y añadir un error nuevo es añadir un método en un solo sitio.

## `@RestControllerAdvice`: el manejador global

```java
// exception/ManejadorGlobalExcepciones.java (resumido)
@RestControllerAdvice
public class ManejadorGlobalExcepciones {

    // 404: el recurso pedido no existe
    @ExceptionHandler(ProductoNoEncontradoException.class)
    public ResponseEntity<RespuestaError> manejarNoEncontrado(ProductoNoEncontradoException ex,
                                                              HttpServletRequest request) {
        return crearRespuesta(HttpStatus.NOT_FOUND, ex.getMessage(), request, Map.of());
    }

    // 409: un mismo método puede atender varias excepciones
    @ExceptionHandler({ ProductoDuplicadoException.class, StockInsuficienteException.class })
    public ResponseEntity<RespuestaError> manejarConflicto(RuntimeException ex, HttpServletRequest request) {
        return crearRespuesta(HttpStatus.CONFLICT, ex.getMessage(), request, Map.of());
    }

    // ...
}
```

- **`@RestControllerAdvice`**: «esta clase da consejos a todos los `@RestController`». Sus métodos se aplican a las excepciones que salgan de cualquier controlador.
- **`@ExceptionHandler(X.class)`**: este método trata las excepciones de tipo `X` (y de sus clases hijas).
- Los parámetros los rellena Spring: la excepción y, si se pide, la petición (`HttpServletRequest`) para saber la ruta.
- **`ResponseEntity<T>`** permite elegir a la vez el **código de estado** y el **cuerpo** de la respuesta: `ResponseEntity.status(estado).body(cuerpo)`.

### El formato común: `RespuestaError`

Todos los errores de la API tienen esta forma:

```java
// dto/RespuestaError.java
public record RespuestaError(
        LocalDateTime fecha,
        int estado,
        String error,
        String mensaje,
        String ruta,
        Map<String, String> errores) {   // solo se rellena en los errores de validación
}
```

```json
{
  "fecha": "2026-10-23T20:01:37.001951",
  "estado": 409,
  "error": "Conflict",
  "mensaje": "Stock insuficiente de 'Teclado': hay 6 y se han pedido 100",
  "ruta": "/api/productos/23/ventas",
  "errores": {}
}
```

Es un DTO más ([lección 2](02-patron-dto.md)): un `record` pensado para lo que necesita el cliente.

## Qué error devuelve cada cosa

| Situación | Excepción | Quién la lanza | Código |
|---|---|---|---|
| El producto no existe | `ProductoNoEncontradoException` | nuestro servicio | **404** Not Found |
| Nombre repetido | `ProductoDuplicadoException` | nuestro servicio | **409** Conflict |
| No hay stock suficiente | `StockInsuficienteException` | nuestro servicio | **409** Conflict |
| Un campo no cumple `@NotBlank`, `@Positive`… | `MethodArgumentNotValidException` | Spring, por `@Valid` | **400** Bad Request |
| El cuerpo no es un JSON válido, o un campo tiene un tipo incorrecto (`"precio": "caro"`) | `HttpMessageNotReadableException` | Spring | **400** Bad Request |
| `/api/productos/abc` (el id no es un número) | `MethodArgumentTypeMismatchException` | Spring | **400** Bad Request |
| URL que no existe / verbo no permitido | `NoResourceFoundException` / `HttpRequestMethodNotSupportedException` | Spring | **404** / **405** |
| Cualquier otra cosa | `Exception` | quien sea | **500** Internal Server Error |

> **¿Cómo se elige el código?** **4xx** = el cliente ha pedido algo incorrecto y puede corregirlo (datos mal, recurso que no existe, conflicto con el estado actual). **5xx** = ha fallado el servidor, y el cliente no puede hacer nada. Un nombre repetido es **409** y no 400 porque la petición está bien formada: el problema es que choca con un dato que ya existe.

### Los errores de validación: un mapa campo → mensaje

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<RespuestaError> manejarValidacion(MethodArgumentNotValidException ex,
                                                        HttpServletRequest request) {
    Map<String, String> errores = new LinkedHashMap<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
        errores.putIfAbsent(error.getField(), error.getDefaultMessage());
    }
    return crearRespuesta(HttpStatus.BAD_REQUEST, "Hay campos con valores no válidos", request, errores);
}
```

`getFieldErrors()` trae un error por cada anotación que ha fallado. El mensaje es el `message = "..."` que pusimos en el DTO. Con `putIfAbsent` se queda solo el primer error de cada campo, para no repetir.

### La red de seguridad: el error 500

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<RespuestaError> manejarErrorInesperado(Exception ex, HttpServletRequest request) {
    // Las excepciones propias de Spring MVC ya saben su código HTTP
    // (URL inexistente → 404, verbo no permitido → 405...): lo respetamos
    if (ex instanceof ErrorResponse errorDeSpring) {
        HttpStatus estado = HttpStatus.valueOf(errorDeSpring.getStatusCode().value());
        return crearRespuesta(estado, ex.getMessage(), request, Map.of());
    }

    // Un error de verdad: se guarda entero en el log para el programador,
    // pero al cliente no se le dan detalles internos (trazas, SQL...)
    log.error("Error inesperado en {} {}", request.getMethod(), request.getRequestURI(), ex);
    return crearRespuesta(HttpStatus.INTERNAL_SERVER_ERROR, "Ha ocurrido un error inesperado", request, Map.of());
}
```

Dos ideas importantes:

1. **Al cliente, un mensaje genérico; al log, todo.** Una traza o un mensaje de la base de datos puede revelar nombres de tablas, versiones de librerías o rutas del servidor, que es información útil para un atacante. El programador lo ve todo en el log.
2. **Si hay varios handlers posibles, gana el más específico.** Una `ProductoNoEncontradoException` también es una `Exception`, pero Spring elige `manejarNoEncontrado` porque su tipo está más cerca. `Exception.class` solo recoge lo que nadie más ha tratado.

El `if` del principio existe porque Spring tiene sus propias excepciones (URL inexistente, verbo no permitido…) que ya saben qué código les corresponde. Esas excepciones implementan la interfaz `ErrorResponse`; sin el `if`, nuestra red de seguridad las convertiría todas en un 500.

## Excepciones comprobadas y no comprobadas

| | Comprobadas (*checked*) | No comprobadas (*unchecked*) |
|---|---|---|
| Heredan de | `Exception` | `RuntimeException` |
| ¿Obligan a `throws` o `try/catch`? | Sí | No |
| `@Transactional` hace *rollback* | No (por defecto) | Sí |
| Ejemplos | `IOException`, `SQLException` | `NullPointerException`, `IllegalArgumentException`, **las nuestras** |

Nuestras excepciones son no comprobadas para que puedan **subir libremente** desde el servicio hasta el manejador global sin ensuciar cada método con `throws`.

## ¿Entonces nunca hay que usar `try/catch`?

Sí, cuando **puedes hacer algo útil** con el error en ese punto: reintentar, usar un valor por defecto, o traducir una excepción técnica a una de negocio. Por ejemplo:

```java
try {
    pasarelaDePago.cobrar(importe);
} catch (TimeoutException e) {
    throw new PagoNoDisponibleException("La pasarela de pago no responde", e);
}
```

Lo que **no** hay que hacer es capturar una excepción solo para devolver un código HTTP desde el controlador: para eso está el manejador global.

## Para saber más: `ProblemDetail`

Existe un estándar para el formato de los errores HTTP (RFC 9457, *Problem Details*), y Spring lo trae ya hecho con la clase `ProblemDetail`. Su JSON tiene campos como `type`, `title`, `status`, `detail` e `instance`. Aquí se usa `RespuestaError` porque se entiende mejor al aprender, pero en proyectos profesionales verás `ProblemDetail` a menudo. La idea es la misma: **un único formato para todos los errores**.

## Cómo comprobarlo

En [`peticiones.http`](../peticiones.http), sección *Lección 4*, hay una petición para cada fila de la tabla de arriba. Fíjate en que todas las respuestas de error tienen exactamente los mismos campos.

Los tests de `ProductoControllerTest` cubren todos los casos: `noEncontradoDevuelve404ConFormatoComun`, `datosNoValidosDevuelven400SinLlegarAlServicio`, `jsonMalFormadoDevuelve400`, `idNoNumericoDevuelve400`, `duplicadoDevuelve409`, `verboNoPermitidoMantiene405`, `errorInesperadoDevuelve500SinDetallesInternos`…

```bash
./mvnw test -Dtest=ProductoControllerTest
```

Estos tests no usan base de datos: `@WebMvcTest` carga el controlador **y los `@RestControllerAdvice`**, y el servicio es un *mock* al que se le dice qué excepción lanzar (`when(...).thenThrow(...)`).

## Errores típicos

- **`try/catch` en cada método del controlador** devolviendo `ResponseEntity` de error. Es lo que el manejador global evita.
- **Capturar una excepción y no hacer nada** (`catch (Exception e) {}`). El error desaparece y nadie sabe por qué los datos están mal.
- **Devolver 500 para errores del cliente** (datos inválidos, recurso inexistente). El 500 es solo para fallos del servidor.
- **Devolver al cliente `ex.getMessage()` de una excepción inesperada.** Puede contener SQL o detalles internos.
- **Olvidar `@RestControllerAdvice`** (o ponerlo en una clase fuera del paquete `com.saseformacion.crud`): los `@ExceptionHandler` no se aplican.

## Preguntas de repaso

1. Recorre el camino completo de `GET /api/productos/99` cuando el 99 no existe: ¿quién lanza la excepción, quién la atrapa y qué JSON recibe el cliente?
2. Si quitas el método `manejarNoEncontrado`, ¿qué código recibiría el cliente al pedir un producto que no existe? ¿Por qué?
3. ¿Por qué el nombre duplicado es un 409 y el nombre vacío un 400?
4. Añade mentalmente la regla «no se puede borrar un producto con stock». ¿Qué tendrías que crear o cambiar para que devuelva un 409?

<details>
<summary>Ver respuestas</summary>

1. `ProductoService.obtenerProducto()` lanza `ProductoNoEncontradoException`. El controlador no la captura, así que sale de él y Spring la pasa a `ManejadorGlobalExcepciones.manejarNoEncontrado`, que devuelve un 404 con `RespuestaError`: `estado` 404, `mensaje` «No existe ningún producto con id 99» y `ruta` `/api/productos/99`.
2. Un **500** «Ha ocurrido un error inesperado»: la excepción la recogería el handler de `Exception.class`. Además, quedaría un error en el log.
3. El nombre vacío es un dato con mala forma, que se detecta mirando solo la petición (400). El duplicado es una petición correcta que choca con un dato que ya existe en el servidor (409).
4. Crear una excepción (por ejemplo `ProductoConStockException extends RuntimeException`), lanzarla en `ProductoService.eliminar()`, y añadirla a la lista del `@ExceptionHandler` de los 409. El controlador no cambia.

</details>
