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

/**
 * Servicio de aplicación responsable de coordinar la carga documental.
 *
 * Responsabilidades:
 * - Validar los datos obligatorios.
 * - Verificar la existencia del legajo en MS-Legajos.
 * - Validar el archivo recibido.
 * - Generar una clave única de almacenamiento.
 * - Coordinar la carga física en Azure Blob Storage.
 * - Registrar los metadatos en PostgreSQL.
 * - Ejecutar una compensación si falla el registro de metadatos.
 */
@Service
public class DocumentoStorageService {

    private final AzureBlobService azureBlobService;
    private final DocumentoValidator documentoValidator;
    private final DocumentoService documentoService;
    private final LegajoClient legajoClient;

    /**
     * Constructor con inyección de dependencias.
     */
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
     * Procesa la carga de un documento asociado a un legajo.
     *
     * @param archivo    archivo recibido mediante multipart/form-data.
     * @param legajoId   identificador UUID del legajo.
     * @param uploadedBy identificador del usuario que realiza la carga.
     * @return entidad Documento con sus metadatos persistidos.
     */
    public Documento cargarDocumento(
            MultipartFile archivo,
            UUID legajoId,
            String uploadedBy) {

        // 1. Validar los datos obligatorios de la operación.
        if (legajoId == null) {
            throw new IllegalArgumentException(
                    "El identificador del legajo es obligatorio.");
        }

        if (uploadedBy == null || uploadedBy.isBlank()) {
            throw new IllegalArgumentException(
                    "El usuario que realiza la carga es obligatorio.");
        }

        // 2. Validar que el legajo exista en MS-Legajos.
        // Esta consulta ocurre antes de subir el archivo a Azure.
        legajoClient.existeLegajo(legajoId.toString());

        // 3. Validar formato, contenido y tamaño del archivo.
        final String tipoMime;

        try {
            tipoMime = documentoValidator.validar(archivo);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "No fue posible leer el archivo recibido.", ex);
        }

        // 4. Generar identificadores y datos de almacenamiento.
        UUID documentoId = UUID.randomUUID();

        String nombreOriginal = archivo.getOriginalFilename();

        String extension = obtenerExtension(nombreOriginal);

        String storageKey = generarStorageKey(
                legajoId, extension);

        OffsetDateTime fechaActual = OffsetDateTime.now();

        try {
            // 5. Subir el archivo físico a Azure Blob Storage.
            try (InputStream contenido = archivo.getInputStream()) {

                azureBlobService.subirArchivo(
                        storageKey,
                        contenido,
                        archivo.getSize(),
                        tipoMime);
            }

            // 6. Construir la entidad con los metadatos documentales.
            Documento documento = new Documento();

            documento.setId(documentoId);
            documento.setLegajoId(legajoId);
            documento.setNombreOriginal(nombreOriginal);
            documento.setNombreVisible(nombreOriginal);
            documento.setTipoMime(tipoMime);
            documento.setTamanoBytes(archivo.getSize());
            documento.setStorageKey(storageKey);
            documento.setEstado("ASOCIADO");
            documento.setUploadedBy(uploadedBy);
            documento.setCreatedAt(fechaActual);
            documento.setUpdatedAt(fechaActual);

            // 7. Persistir los metadatos en PostgreSQL.
            return documentoService.guardar(documento);

        } catch (Exception ex) {

            // 8. Compensar: eliminar el Blob si falla la operación.
            try {
                azureBlobService.eliminarArchivo(storageKey);
            } catch (Exception errorCompensacion) {
                ex.addSuppressed(errorCompensacion);
            }

            throw new IllegalStateException(
                    "No fue posible completar la carga del documento.",
                    ex);
        }
    }

    /**
     * Obtiene la extensión del nombre original del archivo.
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
     * Genera una clave única para almacenar el archivo en Azure.
     *
     * No utiliza el nombre original como identificador físico.
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