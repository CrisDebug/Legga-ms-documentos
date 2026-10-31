package cl.legga.documentos.documento.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentoResponse(
        UUID id,
        UUID legajoId,
        String nombreOriginal,
        String nombreVisible,
        String tipoMime,
        Long tamanoBytes,
        String estado,
        String uploadedBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}