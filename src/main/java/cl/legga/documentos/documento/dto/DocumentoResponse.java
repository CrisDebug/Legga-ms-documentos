
package cl.legga.documentos.documento.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DocumentoResponse(
                UUID id,
                UUID legajoId,
                UUID institucionId,
                List<UUID> requisitoIds,
                String nombreOriginal,
                String nombreVisible,
                String tipoMime,
                Long tamanoBytes,
                String estado,
                String uploadedBy,
                OffsetDateTime createdAt,
                OffsetDateTime updatedAt) {
}
