# Lecciones: del CRUD básico a una API bien hecha

Estas lecciones continúan el CRUD de productos. La versión básica (Controller → Service → Repository, explicada en los [apuntes interactivos de MVC](https://gsanox.github.io/crud-springboot/)) funcionaba, pero tenía problemas que aparecen en cuanto una API crece. Cada lección resuelve uno de ellos con código real del proyecto.

| # | Lección | Problema que resuelve | Ficheros principales |
|---|---|---|---|
| 1 | [Capa de servicio](01-capa-servicio.md) | ¿Dónde van las reglas del negocio? | `service/ProductoService.java` |
| 2 | [Patrón DTO](02-patron-dto.md) | La API exponía la entidad tal cual, y no se validaba nada | `dto/` |
| 3 | [Mappers](03-mappers.md) | El código que convierte entidad ↔ DTO se repetía por todas partes | `mapper/ProductoMapper.java` |
| 4 | [Tratamiento de excepciones](04-excepciones.md) | Cada error salía con un formato distinto (o como un 500) | `exception/` |
| 5 | [Caché](05-cache.md) | Leer lo mismo una y otra vez de la base de datos | `config/CacheConfig.java` |

Están en este orden porque cada una usa lo anterior: el mapper convierte los DTOs, el manejador de excepciones atrapa las que lanza el servicio, y la caché guarda los DTOs que devuelve el servicio.

## Cómo usarlas

1. Lee la lección con el código abierto al lado: cada fragmento indica de qué fichero sale.
2. Arranca la aplicación (`docker compose up -d` y `./mvnw spring-boot:run`) y lanza las peticiones de [`peticiones.http`](../peticiones.http). Se ejecutan directamente desde IntelliJ o desde VS Code con la extensión *REST Client*; también puedes copiarlas a Postman.
3. Responde las preguntas de repaso del final **antes** de abrir la solución.

## Cómo viaja ahora una petición

```text
          JSON                 ProductoRequest              Producto (entidad)
Cliente ────────► Controller ─────────────────► Service ───────────────────► Repository ──► PostgreSQL
   ▲      @Valid      │                           │  ProductoMapper                │
   │                  │                           │  reglas de negocio             │
   │                  │                           │  @Cacheable / @CacheEvict      │
   │      JSON        │      ProductoResponse     │         Producto               │
   └──────────────────┴◄──────────────────────────┴◄───────────────────────────────┘

   Si algo falla en cualquier punto ──► excepción ──► ManejadorGlobalExcepciones ──► JSON de error (400/404/409/500)
```

Lo importante: **la entidad `Producto` nunca sale del servicio**. Hacia fuera solo viajan DTOs.

## Qué ha cambiado respecto a la versión básica

```text
src/main/java/com/saseformacion/crud/
├── config/CacheConfig.java                  NUEVO   activa la caché (lección 5)
├── controller/ProductoController.java       CAMBIA  recibe y devuelve DTOs, @Valid, nuevo endpoint de ventas
├── dto/                                     NUEVO   ProductoRequest, ProductoResponse, VentaRequest, RespuestaError
├── exception/
│   ├── ManejadorGlobalExcepciones.java      NUEVO   @RestControllerAdvice (lección 4)
│   ├── ProductoDuplicadoException.java      NUEVO   regla: nombre único
│   ├── StockInsuficienteException.java      NUEVO   regla: no vender más de lo que hay
│   └── ProductoNoEncontradoException.java   CAMBIA  ya no lleva @ResponseStatus
├── mapper/ProductoMapper.java                NUEVO   entidad ↔ DTO (lección 3)
├── model/Producto.java                      CAMBIA  nuevo campo fechaCreacion
├── repository/ProductoRepository.java       CAMBIA  consultas derivadas existsByNombre...
└── service/ProductoService.java             CAMBIA  DTOs, reglas de negocio, caché
```

En el `pom.xml` se han añadido dos dependencias: `spring-boot-starter-validation` (para `@Valid`, `@NotBlank`…) y `spring-boot-starter-cache`.

Los tests están en `src/test/java`: `ProductoMapperTest` (sin Spring), `ProductoServiceTest` (reglas y caché, contra PostgreSQL) y `ProductoControllerTest` (HTTP y errores, con el servicio simulado).
