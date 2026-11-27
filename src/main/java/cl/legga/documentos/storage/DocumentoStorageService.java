
package cl.legga.documentos.storage;

import cl.legga.documentos.documento.entity.Documento;
import cl.legga.documentos.documento.service.DocumentoService;
import cl.legga.documentos.documento.validation.DocumentoValidator;
import cl.legga.documentos.legajo.LegajoClient;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
public class DocumentoStorageService {

    private final AzureBlobService azureBlobService;
    private final DocumentoValidator documentoValidator;
    private final DocumentoService documentoService;
    private final LegajoClient legajoClient;

    public DocumentoStorageService(
            AzureBlobService azureBlobService,
            DocumentoValidator documentoValidator,
            DocumentoService documentoService,
            LegajoClient legajoClient) {

        this.azureBlobService = azureBlobService;
        this.documentoValidator = documentoValidator;
        this.documentoService = documentoService;
        this.legajoClient = legajoClient;
    }

    /**
     * Procesa la carga de un documento asociado
     * a un requisito documental de un legajo.
     */
    public Documento cargarDocumento(
            MultipartFile archivo,
            UUID legajoId,
            UUID requisitoId,
            String uploadedBy,
            String tenantId,
            String objectId) {

        // 1. Validar los datos obligatorios.

        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException(
                    "Debe seleccionar un archivo.");
        }

        if (legajoId == null) {
            throw new IllegalArgumentException(
                    "El identificador del legajo es obligatorio.");
        }

        if (requisitoId == null) {
            throw new IllegalArgumentException(
                    "El requisito documental es obligatorio.");
        }

        if (uploadedBy == null || uploadedBy.isBlank()) {
            throw new IllegalArgumentException(
                    "El usuario que realiza la carga es obligatorio.");
        }

        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException(
                    "El identificador del tenant es obligatorio.");
        }

        if (objectId == null || objectId.isBlank()) {
            throw new IllegalArgumentException(
                    "El identificador del usuario autenticado es obligatorio.");
        }

        // 2. Validar que el requisito pertenezca
        // a la categoría del legajo y esté activo.

        legajoClient.validarRequisito(
                legajoId,
                requisitoId,
                tenantId,
                objectId);

        // 3. Obtener la institución propietaria del legajo.

        UUID institucionId = legajoClient.obtenerInstitucionId(
                legajoId,
                tenantId,
                objectId);

        if (institucionId == null) {
            throw new IllegalStateException(
                    "No fue posible obtener la institución del legajo.");
        }

        // 4. Validar formato, contenido y tamaño del archivo.

        final String tipoMime;

        try {
            tipoMime = documentoValidator.validar(archivo);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "No fue posible leer el archivo recibido.",
                    ex);
        }

        // 5. Obtener el nombre y generar la clave de almacenamiento.

        String nombreOriginal = archivo.getOriginalFilename();

        if (nombreOriginal == null || nombreOriginal.isBlank()) {
            throw new IllegalArgumentException(
                    "El nombre original del archivo es obligatorio.");
        }

        String extension = obtenerExtension(nombreOriginal);

        String storageKey = generarStorageKey(
                legajoId,
                extension);

        UUID documentoId = UUID.randomUUID();

        OffsetDateTime fechaActual = OffsetDateTime.now();

        boolean archivoSubido = false;

        try {

            // 6. Subir el archivo físico a Azure Blob Storage.

            try (InputStream contenido = archivo.getInputStream()) {

                azureBlobService.subirArchivo(
                        storageKey,
                        contenido,
                        archivo.getSize(),
                        tipoMime);

                archivoSubido = true;
            }

            // 7. Construir la entidad Documento.

            Documento documento = new Documento();

            documento.setId(documentoId);
            documento.setLegajoId(legajoId);
            documento.setInstitucionId(institucionId);
            documento.setNombreOriginal(nombreOriginal);
            documento.setNombreVisible(nombreOriginal);
            documento.setTipoMime(tipoMime);
            documento.setTamanoBytes(archivo.getSize());
            documento.setStorageKey(storageKey);
            documento.setEstado("ASOCIADO");
            documento.setUploadedBy(uploadedBy);
            documento.setCreatedAt(fechaActual);
            documento.setUpdatedAt(fechaActual);

            // 8. Guardar documento, requisito y evento Outbox.

            return documentoService.guardarDocumentoCargado(
                    documento,
                    requisitoId,
                    uploadedBy);

        } catch (Exception ex) {

            // 9. Eliminar el Blob si falló la persistencia.

            if (archivoSubido) {
                try {
                    azureBlobService.eliminarArchivo(storageKey);
                } catch (Exception errorCompensacion) {
                    ex.addSuppressed(errorCompensacion);
                }
            }

            throw new IllegalStateException(
                    "No fue posible completar la carga del documento.",
                    ex);
        }
    }

    /**
     * Obtiene la extensión del archivo.
     */
    private String obtenerExtension(String nombreArchivo) {

        int posicion = nombreArchivo.lastIndexOf('.');

        if (posicion < 0 || posicion == nombreArchivo.length() - 1) {
            throw new IllegalArgumentException(
                    "El archivo no tiene una extensión válida.");
        }

        return nombreArchivo.substring(posicion + 1)
                .toLowerCase(Locale.ROOT);
    }

    /**
     * Genera una clave única para Azure Blob Storage.
     */
    private String generarStorageKey(
            UUID legajoId,
            String extension) {

        return "legajos/"
                + legajoId
                + "/"
                + UUID.randomUUID()
                + "."
                + extension;
    }
}
