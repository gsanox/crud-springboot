# CRUD de productos con Spring Boot

Una API REST sencilla de productos para explicar **MVC** y la arquitectura en capas **Controller → Service → Repository** con Spring Boot. Es material de clase para DAM y DAW: el código es explícito a propósito (sin Lombok, con getters y setters escritos a mano) y está en español.

**Lecciones interactivas**: [Spring Boot por capas](https://gsanox.github.io/crud-springboot/), con el itinerario completo. Cada lección usa el código del proyecto, tiene algo que tocar (animaciones, simuladores) y termina con un test.

0. [MVC explicado paso a paso](https://gsanox.github.io/crud-springboot/mvc.html): el viaje de una petición por las capas.
1. [Capa de servicio](docs/lecciones/01-capa-servicio.md): reglas de negocio, `@Transactional` y consultas derivadas.
2. [Patrón DTO](docs/lecciones/02-patron-dto.md): `record` de entrada y de salida, y validación con `@Valid`.
3. [Mappers](docs/lecciones/03-mappers.md): conversión entidad ↔ DTO en un solo sitio.
4. [Tratamiento de excepciones](docs/lecciones/04-excepciones.md): `@RestControllerAdvice` y un formato común para los errores.
5. [Caché](https://gsanox.github.io/crud-springboot/cache.html): `@Cacheable`, `@CachePut` y `@CacheEvict`, con un laboratorio que simula la API.

## Qué lleva

- Spring Boot 4.1 y Java 21, con Maven.
- Spring Web MVC (`@RestController`) y Spring Data JPA con Hibernate.
- Bean Validation (`@Valid`, `@NotBlank`…) y Spring Cache con una caché en memoria.
- PostgreSQL 17 en Docker.
- Tests del controlador con `@WebMvcTest` y un servicio falso (`@MockitoBean`), tests del servicio (reglas y caché) contra la base de datos y tests del mapper sin Spring.

## Cómo arrancarlo

Necesitas Java 21 y Docker.

```bash
docker compose up -d        # PostgreSQL en localhost:5432 (base de datos crud, usuario y contraseña postgres)
./mvnw spring-boot:run      # la API en http://localhost:8080
./mvnw test                 # todos los tests (con PostgreSQL en marcha)
```

Con cada push, GitHub Actions compila el proyecto y pasa los tests contra un PostgreSQL propio ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)): el commit queda marcado en verde o en rojo.

La conexión se puede cambiar con las variables de entorno `DB_URL`, `DB_USERNAME` y `DB_PASSWORD`. La tabla `productos` la crea Hibernate a partir de la entidad (`ddl-auto=update`, solo para desarrollo), y el SQL que genera sale en la consola.

## Endpoints

| Operación | Petición | Respuesta |
|---|---|---|
| Listar | `GET /api/productos` | `200 OK` |
| Buscar uno | `GET /api/productos/{id}` | `200 OK`, o `404` si no existe |
| Crear | `POST /api/productos` con el producto en JSON | `201 Created`, `400` si los datos no son válidos, o `409` si el nombre ya existe |
| Actualizar | `PUT /api/productos/{id}` con el producto en JSON | `200 OK`, `400`, `404` o `409` |
| Borrar | `DELETE /api/productos/{id}` | `204 No Content`, o `404` |
| Vender | `POST /api/productos/{id}/ventas` con `{"cantidad": 3}` | `200 OK` con el stock restado, o `409` si no hay suficiente |

Todos los errores devuelven el mismo JSON:

```json
{
  "fecha": "2026-10-23T20:01:36.96066",
  "estado": 400,
  "error": "Bad Request",
  "mensaje": "Hay campos con valores no válidos",
  "ruta": "/api/productos",
  "errores": { "nombre": "El nombre es obligatorio" }
}
```

Por ejemplo:

```bash
curl -X POST http://localhost:8080/api/productos \
  -H "Content-Type: application/json" \
  -d '{"nombre": "Teclado", "descripcion": "Teclado mecánico", "precio": 49.99, "stock": 10}'
```

En [`docs/peticiones.http`](docs/peticiones.http) hay una petición de ejemplo para cada caso de las lecciones, lista para lanzar desde IntelliJ o VS Code.

## Dónde está cada capa

```text
src/main/java/com/saseformacion/crud/
├── CrudApplication.java      arranque
├── controller/               HTTP ↔ Java: URLs, @Valid y códigos de estado; recibe y devuelve DTOs
├── service/                  lógica de negocio, transacciones y caché
├── repository/               acceso a datos (Spring Data JPA genera los métodos)
├── model/                    la entidad Producto: una fila de la tabla productos
├── dto/                      lo que entra y sale de la API (records), con sus validaciones
├── mapper/                   conversión entidad ↔ DTO
├── exception/                excepciones de negocio y ManejadorGlobalExcepciones (@RestControllerAdvice)
└── config/                   CacheConfig (@EnableCaching)
```

## Licencia

[MIT](LICENSE). Preparado para las clases de [SASE Formación](https://saseformacion.es).
