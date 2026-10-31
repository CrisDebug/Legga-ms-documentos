package cl.legga.documentos.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Servicio responsable de interactuar con Azure Blob Storage.
 *
 * Responsabilidades:
 * - Obtener acceso al contenedor documental.
 * - Verificar la existencia del contenedor.
 * - Subir archivos al almacenamiento.
 * - Generar enlaces temporales de descarga mediante SAS.
 * - Eliminar archivos como operación técnica de compensación.
 *
 * Los metadatos documentales se administran en PostgreSQL.
 * Este servicio administra los archivos físicos almacenados en Azure.
 */
@Service
public class AzureBlobService {

    /**
     * Cliente del contenedor privado de documentos.
     */
    private final BlobContainerClient containerClient;

    /**
     * Constructor del servicio.
     *
     * Obtiene el cliente del contenedor usando el cliente principal
     * de Azure Blob Storage y el nombre configurado en properties.
     *
     * @param blobServiceClient cliente principal de Azure Blob.
     * @param containerName     nombre del contenedor configurado.
     */
    public AzureBlobService(
            BlobServiceClient blobServiceClient,
            @Value("${azure.storage.container-name}") String containerName) {

        this.containerClient = blobServiceClient.getBlobContainerClient(containerName);
    }

    /**
     * Comprueba si existe el contenedor documental.
     *
     * @return true si el contenedor existe; false en caso contrario.
     */
    public boolean contenedorExiste() {
        return containerClient.exists();
    }

    /**
     * Sube un archivo al contenedor de Azure Blob Storage.
     *
     * @param storageKey identificador único de almacenamiento.
     * @param contenido  flujo de datos del archivo.
     * @param tamano     tamaño del archivo en bytes.
     * @param tipoMime   tipo MIME validado previamente.
     * @return clave utilizada para identificar el Blob.
     */
    public String subirArchivo(
            String storageKey,
            InputStream contenido,
            long tamano,
            String tipoMime) {

        // Obtiene el cliente del Blob correspondiente al archivo.
        BlobClient blobClient = containerClient.getBlobClient(storageKey);

        // Sube el contenido sin sobrescribir un Blob existente.
        blobClient.upload(contenido, tamano, false);

        // Configura el tipo MIME para que el archivo se interprete
        // correctamente cuando sea descargado o visualizado.
        BlobHttpHeaders headers = new BlobHttpHeaders()
                .setContentType(tipoMime);

        blobClient.setHttpHeaders(headers);

        // Devuelve la clave que se almacenará en PostgreSQL.
        return storageKey;
    }

    /**
     * Genera una URL temporal de descarga para un Blob.
     *
     * La URL SAS:
     * - Permite únicamente lectura del archivo.
     * - Expira después de 5 minutos.
     * - Se firma utilizando la credencial de la cuenta de Azure
     * configurada en el cliente.
     *
     * La URL permite descargar el archivo sin entregar directamente
     * las credenciales de Azure al consumidor.
     *
     * IMPORTANTE:
     * La URL SAS es una credencial temporal. No debe registrarse
     * completa en logs ni almacenarse como dato permanente.
     *
     * @param storageKey clave del archivo almacenado.
     * @return URL temporal firmada para descargar el archivo.
     */
    public String generarUrlDescargaSas(String storageKey) {

        // Obtiene el cliente del Blob específico del documento.
        BlobClient blobClient = containerClient.getBlobClient(storageKey);

        // Define el vencimiento de la SAS en UTC.
        // El enlace dejará de ser válido después de 5 minutos.
        OffsetDateTime expiracion = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5);

        // Define los permisos que tendrá el enlace.
        // Solo se habilita lectura; no permite modificar ni eliminar.
        BlobSasPermission permisos = new BlobSasPermission()
                .setReadPermission(true);

        // Construye los valores que se utilizarán para firmar la SAS.
        BlobServiceSasSignatureValues valoresSas = new BlobServiceSasSignatureValues(expiracion, permisos);

        // Genera el token SAS utilizando la credencial del cliente.
        String tokenSas = blobClient.generateSas(valoresSas);

        // Construye la URL completa agregando el token SAS al Blob.
        return blobClient.getBlobUrl() + "?" + tokenSas;
    }

    /**
     * Elimina un Blob como operación técnica de compensación.
     *
     * Este método no representa la desvinculación funcional
     * de un documento en LEGGA.
     *
     * @param storageKey clave del archivo almacenado.
     */
    public void eliminarArchivo(String storageKey) {

        // Obtiene el cliente del archivo.
        BlobClient blobClient = containerClient.getBlobClient(storageKey);

        // Elimina el Blob si existe.
        blobClient.deleteIfExists();
    }
}