# CRUD de productos con Spring Boot

Una API REST sencilla de productos para explicar **MVC** y la arquitectura en capas **Controller → Service → Repository** con Spring Boot. Es material de clase para DAM y DAW: el código es explícito a propósito (sin Lombok, con getters y setters escritos a mano) y está en español.

**Apuntes interactivos**: [MVC explicado paso a paso](https://gsanox.github.io/crud-springboot/), con el viaje de una petición por las capas, el código de cada paso y una autoevaluación.

## Qué lleva

- Spring Boot 4.1 y Java 21, con Maven.
- Spring Web MVC (`@RestController`) y Spring Data JPA con Hibernate.
- PostgreSQL 17 en Docker.
- Tests del controlador con `@WebMvcTest` y un servicio falso (`@MockitoBean`), y tests del servicio contra la base de datos.

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
| Crear | `POST /api/productos` con el producto en JSON | `201 Created` |
| Actualizar | `PUT /api/productos/{id}` con el producto en JSON | `200 OK`, o `404` |
| Borrar | `DELETE /api/productos/{id}` | `204 No Content`, o `404` |

Por ejemplo:

```bash
curl -X POST http://localhost:8080/api/productos \
  -H "Content-Type: application/json" \
  -d '{"nombre": "Teclado", "descripcion": "Teclado mecánico", "precio": 49.99, "stock": 10}'
```

## Dónde está cada capa

```text
src/main/java/com/saseformacion/crud/
├── CrudApplication.java      arranque
├── controller/               HTTP ↔ Java: URLs, cuerpo de la petición, códigos de estado
├── service/                  lógica de negocio y transacciones
├── repository/               acceso a datos (Spring Data JPA genera los métodos)
├── model/                    la entidad Producto: una fila de la tabla productos
└── exception/                ProductoNoEncontradoException → 404
```

## Licencia

[MIT](LICENSE). Preparado para las clases de [SASE Formación](https://saseformacion.es).
