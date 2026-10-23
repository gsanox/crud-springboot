# Lección 2 · El patrón DTO

[← Capa de servicio](01-capa-servicio.md) · [Índice](README.md) · [Siguiente: Mappers →](03-mappers.md)

## Qué vas a aprender

- Qué es un **DTO** (*Data Transfer Object*) y qué problemas tiene exponer la entidad directamente.
- Por qué hay un DTO para lo que **entra** (`Request`) y otro para lo que **sale** (`Response`).
- Qué es un `record` de Java y por qué encaja tan bien con los DTOs.
- Cómo validar los datos de entrada con `@Valid`, `@NotBlank`, `@Positive`…

## El problema: la entidad hacía de todo

En la versión básica, el controlador recibía y devolvía directamente la entidad `Producto`:

```java
// Versión básica (antes)
@PostMapping
public Producto crear(@RequestBody Producto producto) { ... }
```

Funciona, pero la misma clase estaba haciendo tres trabajos a la vez: **tabla de la base de datos**, **formato del JSON de entrada** y **formato del JSON de salida**. Eso trae problemas:

| Problema | Ejemplo en este proyecto |
|---|---|
| El cliente puede mandar campos que no debería | Podría enviar `"id": 3` o `"fechaCreacion": "2000-01-01"` y colarlos en la base de datos. |
| Cambiar la tabla cambia la API | Renombrar la columna `stock` rompería a todas las apps que usan el JSON. |
| No se pueden añadir campos calculados | ¿Dónde pongo `disponible` (stock > 0) si no es una columna? |
| Se expone todo | Si mañana la entidad tiene `precioCoste` o una contraseña, saldría en el JSON. |
| Problemas con relaciones | Con relaciones JPA (`@OneToMany`…) aparecen bucles infinitos al convertir a JSON y errores de carga perezosa. |

## La solución: una clase para cada trabajo

Un **DTO** es una clase cuyo único fin es **transportar datos** entre capas o hacia fuera. No tiene lógica ni está ligada a la base de datos.

```text
                ProductoRequest                         ProductoResponse
Cliente ──JSON──► (lo que ENTRA)  ──►  Producto  ──►   (lo que SALE)   ──JSON──► Cliente
                  nombre                (entidad)       id
                  descripcion           id              nombre
                  precio                nombre          descripcion
                  stock                 descripcion     precio
                                        precio          stock
                                        stock           disponible      ← calculado
                                        fechaCreacion   fechaCreacion
```

- **`ProductoRequest`** no tiene `id` ni `fechaCreacion`: es **imposible** que el cliente los envíe, porque no hay dónde ponerlos. Si los manda en el JSON, se ignoran.
- **`ProductoResponse`** añade `disponible`, que no existe en la tabla.
- **La entidad `Producto`** solo se ocupa de la tabla, y ya **no sale del servicio**.

## Los DTOs del proyecto son `record`

```java
// dto/ProductoResponse.java
public record ProductoResponse(
        Long id,
        String nombre,
        String descripcion,
        BigDecimal precio,
        Integer stock,
        boolean disponible,
        LocalDateTime fechaCreacion) {
}
```

Un `record` (Java 16+) es una clase pensada para **llevar datos**. Con esa línea, Java genera solo:

- un constructor con todos los campos,
- un método para leer cada campo: **`response.nombre()`** (ojo: no `getNombre()`),
- `equals()`, `hashCode()` y `toString()`.

Y es **inmutable**: los campos son `final` y no hay *setters*. Una vez creado, no cambia. Eso es justo lo que se quiere en un DTO: es una «foto» de los datos.

¿Por qué la entidad `Producto` **no** es un `record`? Porque JPA necesita un constructor vacío y poder modificar los campos (por eso tiene *setters*). Entidad → clase normal; DTO → `record`.

> Spring y Jackson (la librería que convierte JSON ↔ Java) entienden los `record` sin configurar nada.

## Un DTO no tiene que parecerse a la entidad

El endpoint de ventas solo necesita un dato, así que su DTO solo tiene uno:

```java
// dto/VentaRequest.java  →  POST /api/productos/1/ventas  con  {"cantidad": 3}
public record VentaRequest(
        @NotNull(message = "La cantidad es obligatoria")
        @Positive(message = "La cantidad tiene que ser mayor que 0")
        Integer cantidad) {
}
```

Y `RespuestaError` ([lección 4](04-excepciones.md)) es un DTO que no tiene nada que ver con ninguna tabla. Los DTOs se diseñan pensando en **qué necesita el cliente**, no en cómo es la base de datos.

## Validación: comprobar los datos al entrar

Como `ProductoRequest` representa justo lo que llega del cliente, es el sitio ideal para decir **qué datos son válidos**:

```java
// dto/ProductoRequest.java
public record ProductoRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede tener más de 100 caracteres")
        String nombre,

        @Size(max = 500, message = "La descripción no puede tener más de 500 caracteres")
        String descripcion,

        @NotNull(message = "El precio es obligatorio")
        @Positive(message = "El precio tiene que ser mayor que 0")
        @Digits(integer = 8, fraction = 2, message = "El precio admite como mucho 8 cifras enteras y 2 decimales")
        BigDecimal precio,

        @NotNull(message = "El stock es obligatorio")
        @PositiveOrZero(message = "El stock no puede ser negativo")
        Integer stock) {
}
```

Las anotaciones por sí solas no hacen nada: hay que pedir que se comprueben con **`@Valid`** en el controlador:

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public ProductoResponse crear(@Valid @RequestBody ProductoRequest request) {
    return productoService.crear(request);
}
```

Si algún campo no cumple, Spring lanza `MethodArgumentNotValidException` **antes de entrar en el método**: el servicio ni se entera. El manejador de la [lección 4](04-excepciones.md) la convierte en un **400** con la lista de errores:

```json
{
  "estado": 400,
  "mensaje": "Hay campos con valores no válidos",
  "errores": {
    "nombre": "El nombre es obligatorio",
    "precio": "El precio tiene que ser mayor que 0"
  }
}
```

### Anotaciones de validación más usadas

| Anotación | Comprueba | Para |
|---|---|---|
| `@NotNull` | que no sea `null` | cualquier tipo |
| `@NotBlank` | que no sea `null`, ni `""`, ni solo espacios | `String` |
| `@NotEmpty` | que no sea `null` ni esté vacío | `String`, listas |
| `@Size(min, max)` | longitud | `String`, listas |
| `@Positive` / `@PositiveOrZero` | > 0 / ≥ 0 | números |
| `@Min` / `@Max` | valor mínimo / máximo | números |
| `@Digits(integer, fraction)` | número de cifras | `BigDecimal` |
| `@Email` | formato de email | `String` |
| `@Pattern(regexp)` | que encaje con una expresión regular | `String` |
| `@Past` / `@Future` | fecha pasada / futura | fechas |

> **Validación del DTO vs. reglas del servicio.** El DTO comprueba que los datos tienen **buena forma** sin mirar nada más («el precio es positivo»). Las reglas que necesitan consultar la base de datos («el nombre no está repetido», «hay stock suficiente») van en el **servicio**.

### ¿Y el `nullable = false` de la entidad?

`@Column(nullable = false)` solo define cómo se crea la tabla. Si llega un `null`, el error lo da PostgreSQL al guardar, y la respuesta era un **500** poco claro. Con la validación del DTO el error se detecta **antes**, y el cliente recibe un **400** que dice exactamente qué campo está mal. Las dos cosas se complementan: la base de datos es la última barrera.

## ¿Dónde se usan los DTOs?

```text
Controller  ←── DTOs ──→  Service  ←── entidades ──→  Repository
```

- El **controlador** solo conoce DTOs.
- El **servicio** recibe y devuelve DTOs, pero por dentro trabaja con entidades.
- El **repositorio** solo conoce entidades.

La conversión entre unos y otros la hace el **mapper**, que es la siguiente lección.

## Cómo comprobarlo

En [`peticiones.http`](../peticiones.http), sección *Lección 2*:

- Crea un producto enviando `"id": 999` y `"fechaCreacion": "2000-01-01T00:00:00"` → se ignoran: el `id` lo genera la base de datos y la fecha es la de ahora.
- Fíjate en el campo `disponible` de la respuesta: no está en la tabla.
- Envía un producto con el nombre vacío y el precio negativo → **400** con los dos errores.

Test: `ProductoControllerTest.datosNoValidosDevuelven400SinLlegarAlServicio` comprueba además que **el servicio no se llega a llamar** (`verifyNoInteractions`).

## Errores típicos

- **Olvidar `@Valid`.** Las anotaciones se ignoran sin dar ningún error: todo parece funcionar… y se cuelan datos inválidos.
- **Llamar a `request.getNombre()`** en un `record`. El método se llama `request.nombre()`.
- **Usar `@NotNull` en un `String` obligatorio.** Dejaría pasar `""` y `"   "`. Para textos, `@NotBlank`.
- **Usar `@NotBlank` en un número.** Es solo para textos; en números, `@NotNull`.
- **Devolver la entidad «solo en este endpoint».** En cuanto un endpoint la expone, todos los problemas de la tabla de arriba vuelven.

## Preguntas de repaso

1. ¿Por qué `ProductoRequest` no tiene el campo `id`? ¿Qué pasa si el cliente lo manda en el JSON?
2. ¿Por qué `ProductoResponse` puede ser un `record` y `Producto` no?
3. Un cliente envía `{"nombre": "Ratón", "precio": 10}`. ¿Qué código recibe y por qué? ¿Se ejecuta algo del servicio?
4. ¿Dónde comprobarías «el email tiene un formato válido»? ¿Y «el email no está ya registrado»?

<details>
<summary>Ver respuestas</summary>

1. Porque el `id` lo decide la base de datos, nunca el cliente. Si lo manda, Jackson lo ignora porque el `record` no tiene ese campo.
2. Un DTO solo transporta datos y se crea de una vez, así que puede ser inmutable. JPA necesita un constructor vacío y *setters* para rellenar y modificar la entidad, cosa que un `record` no permite.
3. Un **400**: falta `stock`, que tiene `@NotNull`. La validación ocurre al entrar en el controlador, así que el servicio no se llama.
4. El formato, en el DTO con `@Email` (no necesita consultar nada). Que no esté registrado, en el servicio, porque hay que preguntar a la base de datos.

</details>
