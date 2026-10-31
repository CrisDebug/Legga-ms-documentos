# LEGGA — Microservicio de Documentos

Microservicio encargado de gestionar los documentos asociados a los legajos de LEGGA. Mantiene los metadatos en PostgreSQL y utiliza Azure Blob Storage para almacenar los archivos.

## Tecnologías

- Java 17
- Spring Boot
- Maven
# LEGGA — Microservicio de Documentos

Microservicio encargado de gestionar los documentos asociados a los legajos de LEGGA. Mantiene los metadatos en PostgreSQL y utiliza Azure Blob Storage para almacenar los archivos.

## Tecnologías

- Java 17
- Spring Boot
- Maven
- PostgreSQL
- Azure Blob Storage
- Docker

## Arquitectura

`Frontend Angular → MS-Documentos → PostgreSQL y Azure Blob Storage`
# LEGGA — Microservicio de Documentos

Microservicio encargado de gestionar los documentos asociados a los legajos de LEGGA. Mantiene los metadatos en PostgreSQL y utiliza Azure Blob Storage para almacenar los archivos.

## Tecnologías

- Java 17
- Spring Boot
- Maven
- PostgreSQL
- Azure Blob Storage
- Docker

## Arquitectura

`Frontend Angular → MS-Documentos → PostgreSQL y Azure Blob Storage`

MS-Documentos se comunica con MS-Legajos para validar el legajo asociado al documento.

## Ejecución local

Desde el directorio que contiene la configuración Docker Compose:

```bash
docker compose up --build
```

## Información del servicio

| Elemento | Valor |
|---|---|
| Puerto | 8080 |
| Swagger UI | `/swagger-ui/index.html` |
| Microservicio relacionado | MS-Legajos |
| Almacenamiento de metadatos | PostgreSQL |
| Almacenamiento de archivos | Azure Blob Storage |

## Endpoints principales

| Método | Endpoint | Descripción |
|---|---|---|
| POST | `/api/documentos` | Registrar un documento |
| GET | `/api/documentos` | Consultar documentos |
| GET | `/api/documentos/{id}` | Consultar un documento |
| GET | `/api/documentos/legajo/{legajoId}` | Consultar documentos de un legajo |
| PATCH | `/api/documentos/{id}/desvincular` | Desvincular un documento |

## Avances

- Hito 07: validación de la comunicación con MS-Legajos.
- Hito 08: validación de respuestas para legajos inexistentes, archivados y servicio no disponible.
- Respaldo inicial del código en GitHub.

## Documentación

Los informes técnicos y documentos del proyecto se organizan en la carpeta `/docs`.

## Configuración

La aplicación utiliza variables de entorno para configurar la conexión a PostgreSQL, Azure Blob Storage y MS-Legajos.

No se deben almacenar credenciales directamente en el repositorio.