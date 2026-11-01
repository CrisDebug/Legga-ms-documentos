# Hito técnico: Actualización del nombre visible del documento

## Objetivo
Permitir que el usuario cambie el nombre con el que se presenta un documento en LEGGA, sin modificar el archivo físico almacenado en Azure Blob Storage.

## Endpoint
PATCH /api/documentos/{id}/nombre

## DTO
ActualizarNombreDocumentoRequest

Recibe el campo:
- nombreVisible: nombre que se mostrará en la aplicación.

## Implementación
El controlador recibe el UUID del documento y el DTO de actualización.
El servicio busca el documento en PostgreSQL, valida que el nombre no esté vacío y que no supere los 255 caracteres, actualiza nombreVisible y actualiza updatedAt.

El cambio conserva:
- nombreOriginal
- storageKey
- tipoMime
- tamanoBytes
- estado del documento
- archivo físico almacenado en Azure Blob Storage

## Prueba funcional
Se ejecutó la petición desde Postman con el documento:

ID: 273832de-6294-4eeb-b7cc-b6b696dda460

Nombre original:
LEGGA_Prueba_Imagen_Identificacion.jpg

Nombre visible actualizado:
Identificacion_actualizada.jpg

Resultado:
HTTP 200 OK

La respuesta confirmó que nombreVisible fue actualizado y nombreOriginal permaneció sin cambios.

## Evidencia
La captura de Postman se encuentra en:
evidencias/evidencia-nombre-visible.png

## Resultado del hito
Actualización del nombre visible implementada y probada correctamente.
