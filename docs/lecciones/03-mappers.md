# Lección 3 · Mappers

[← Patrón DTO](02-patron-dto.md) · [Índice](README.md) · [Siguiente: Excepciones →](04-excepciones.md)

## Qué vas a aprender

- Qué es un **mapper** y por qué conviene tener la conversión entidad ↔ DTO en un solo sitio.
- Las cuatro operaciones típicas de un mapper y por qué actualizar es distinto de crear.
- Cómo se prueba un mapper sin Spring.
- Qué es MapStruct y por qué aquí no se usa.

## El problema: convertir en todas partes

Con DTOs ([lección 2](02-patron-dto.md)) aparece un trabajo nuevo: **pasar los datos de un objeto a otro**. Sin un mapper, el servicio quedaría así:

```java
// Sin mapper (NO es el código del proyecto)
public ProductoResponse buscarPorId(Long id) {
    Producto p = obtenerProducto(id);
    return new ProductoResponse(p.getId(), p.getNombre(), p.getDescripcion(),
            p.getPrecio(), p.getStock(), p.getStock() > 0, p.getFechaCreacion());
}

public ProductoResponse crear(ProductoRequest request) {
    Producto p = new Producto(request.nombre(), request.descripcion(), request.precio(), request.stock());
    // ...
    return new ProductoResponse(p.getId(), p.getNombre(), p.getDescripcion(),
            p.getPrecio(), p.getStock(), p.getStock() > 0, p.getFechaCreacion());  // ← otra vez
}
// ... y lo mismo en listar(), actualizar(), vender()
```

El mismo `new ProductoResponse(...)` repetido cinco veces. Si mañana se añade un campo, o cambia cómo se calcula `disponible`, hay que acordarse de cambiarlo en todos los sitios. Y en algún sitio se olvida.

## La solución: una clase que solo convierte

```java
// mapper/ProductoMapper.java
@Component
public class ProductoMapper {

    // DTO de entrada → entidad nueva (sin id ni fecha: los pone el servidor)
    public Producto toEntity(ProductoRequest request) {
        return new Producto(request.nombre(), request.descripcion(), request.precio(), request.stock());
    }

    // Copia los datos del DTO sobre una entidad que ya existe (para actualizar)
    public void updateEntity(Producto producto, ProductoRequest request) {
        producto.setNombre(request.nombre());
        producto.setDescripcion(request.descripcion());
        producto.setPrecio(request.precio());
        producto.setStock(request.stock());
    }

    // Entidad → DTO de salida
    public ProductoResponse toResponse(Producto producto) {
        return new ProductoResponse(
                producto.getId(),
                producto.getNombre(),
                producto.getDescripcion(),
                producto.getPrecio(),
                producto.getStock(),
                producto.getStock() > 0,         // ← la regla de "disponible", en un único sitio
                producto.getFechaCreacion());
    }

    public List<ProductoResponse> toResponseList(List<Producto> productos) {
        return productos.stream()
                .map(this::toResponse)
                .toList();
    }
}
```

Y el servicio queda limpio, sin detalles de conversión:

```java
public ProductoResponse buscarPorId(Long id) {
    return productoMapper.toResponse(obtenerProducto(id));
}
```

### ¿Por qué `updateEntity` y no `toEntity` al actualizar?

Al actualizar, la entidad **ya existe** y tiene datos que el cliente no envía: el `id` y la `fechaCreacion`. Si creáramos una entidad nueva con `toEntity`, esos campos quedarían a `null`:

- sin `id`, `save()` haría un `INSERT` (un producto nuevo) en vez de un `UPDATE`;
- y se perdería la fecha de creación.

Por eso `actualizar()` primero carga la entidad de la base de datos y luego **copia encima** solo lo que viene en el DTO:

```java
// service/ProductoService.java
Producto producto = obtenerProducto(id);            // entidad con id y fecha
productoMapper.updateEntity(producto, request);     // solo cambia nombre, descripción, precio y stock
return productoMapper.toResponse(productoRepository.save(producto));
```

### `.stream().map(...).toList()`

`toResponseList` recorre la lista de entidades, convierte cada una con `toResponse` y junta los resultados en una lista nueva. Es lo mismo que este bucle, en una línea:

```java
List<ProductoResponse> resultado = new ArrayList<>();
for (Producto p : productos) {
    resultado.add(toResponse(p));
}
```

`toList()` devuelve además una lista **que no se puede modificar**, cosa que viene bien para la caché ([lección 5](05-cache.md)).

## ¿Por qué `@Component`?

Para que Spring cree un objeto `ProductoMapper` y lo **inyecte** en el servicio por el constructor, igual que el repositorio:

```java
public ProductoService(ProductoRepository productoRepository, ProductoMapper productoMapper) { ... }
```

`@Component` es la anotación genérica de «esto es un bean». `@Service`, `@Repository` y `@RestController` son variantes de `@Component` con un nombre que dice de qué capa son.

> **Alternativa:** muchos proyectos hacen el mapper con métodos `static` (`ProductoMapper.toResponse(p)`). También vale; con `@Component` el mapper se puede sustituir por un *mock* en los tests y sigue el mismo estilo que el resto de capas.

## ¿Dónde se llama al mapper?

En el **servicio**. Es la frontera: hacia arriba (controlador) solo salen DTOs, y hacia abajo (repositorio) solo van entidades. Si se mapeara en el controlador, la entidad tendría que salir del servicio, que es lo que queríamos evitar.

## Un mapper se prueba sin Spring

Como el mapper solo transforma objetos, no necesita base de datos ni contexto de Spring: se crea con `new` y se prueba en milisegundos.

```java
// src/test/java/.../mapper/ProductoMapperTest.java
class ProductoMapperTest {

    private final ProductoMapper mapper = new ProductoMapper();

    @Test
    void updateEntityNoTocaElIdNiLaFecha() {
        Producto producto = new Producto("Ratón", null, new BigDecimal("19.90"), 5);
        producto.setId(3L);
        // ...
        mapper.updateEntity(producto, new ProductoRequest("Ratón inalámbrico", "Bluetooth", new BigDecimal("24.90"), 2));

        assertEquals(3L, producto.getId());
        // ...
    }
}
```

```bash
./mvnw test -Dtest=ProductoMapperTest
```

## MapStruct: el mapper que se escribe solo

Escribir mappers a mano es repetitivo, sobre todo con entidades grandes. **MapStruct** es una librería que genera el código del mapper a partir de una interfaz:

```java
// Ejemplo con MapStruct (NO se usa en este proyecto)
@Mapper(componentModel = "spring")
public interface ProductoMapper {
    ProductoResponse toResponse(Producto producto);   // MapStruct escribe el código al compilar
}
```

Copia automáticamente los campos que se llaman igual, y con anotaciones (`@Mapping`) se configuran los que no. En este proyecto el mapper está **escrito a mano a propósito**: así se ve exactamente qué hace, y cuando uses MapStruct sabrás qué código está generando por ti.

## Errores típicos

- **Usar `toEntity` para actualizar.** Se pierde el `id` (se crea un producto nuevo) y la fecha de creación.
- **Poner lógica de negocio en el mapper** (comprobar stock, lanzar excepciones…). El mapper solo convierte; las reglas van en el servicio. Un cálculo simple de presentación, como `disponible`, sí puede ir aquí.
- **Olvidar un campo** al añadirlo a la entidad y al DTO. El test del mapper es el que lo detecta.
- **Llamar al mapper desde el controlador** pasándole entidades: obliga a que la entidad salga del servicio.

## Preguntas de repaso

1. Si se añade el campo `categoria` a la entidad y a los dos DTOs, ¿cuántas clases hay que tocar para que viaje en las respuestas? ¿Y sin mapper?
2. ¿Qué pasaría exactamente si `actualizar()` hiciera `productoRepository.save(productoMapper.toEntity(request))`?
3. ¿Por qué el test del mapper no lleva `@SpringBootTest`?
4. ¿Por qué el mapper se llama desde el servicio y no desde el controlador?

<details>
<summary>Ver respuestas</summary>

1. Además de la entidad y los DTOs, solo el mapper (`toEntity`, `updateEntity` y `toResponse`). Sin mapper, cada método del servicio que construye un DTO.
2. La entidad nueva tendría `id = null`, así que `save()` haría un `INSERT`: se crearía un producto nuevo en vez de modificar el existente, y sin fecha de creación.
3. Porque el mapper no depende de nada de Spring ni de la base de datos: se puede crear con `new`. El test es más rápido y más sencillo.
4. Porque el servicio es la frontera entre DTOs y entidades: si el controlador mapeara, la entidad tendría que salir del servicio.

</details>
