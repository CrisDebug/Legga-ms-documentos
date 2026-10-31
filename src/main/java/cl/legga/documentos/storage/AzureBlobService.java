
package cl.legga.documentos.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobHttpHeaders;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * Servicio responsable de interactuar con Azure Blob Storage.
 *
 * Responsabilidades:
 * - Obtener acceso al contenedor documental.
 * - Verificar la existencia del contenedor.
 * - Subir archivos al almacenamiento.
 * - Eliminar archivos como operación de compensación
 * ante errores durante el proceso de persistencia.
 *
 * Los metadatos documentales se administran en PostgreSQL.
 * Este servicio solamente administra los archivos físicos.
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
     * @return true si existe; false en caso contrario.
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

        // Obtiene el cliente del Blob correspondiente.
        BlobClient blobClient = containerClient.getBlobClient(storageKey);

        // Sube el contenido del archivo sin sobrescribir otro Blob.
        blobClient.upload(contenido, tamano, false);

        // Configura el tipo MIME para su correcta interpretación.
        BlobHttpHeaders headers = new BlobHttpHeaders()
                .setContentType(tipoMime);

        blobClient.setHttpHeaders(headers);

        // Devuelve la clave que se almacenará en PostgreSQL.
        return storageKey;
    }

    /**
     * Elimina un Blob como operación técnica de compensación.
     *
     * IMPORTANTE:
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
