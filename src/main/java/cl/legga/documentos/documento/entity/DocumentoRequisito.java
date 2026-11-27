
package cl.legga.documentos.documento.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "documento_requisito", schema = "documentos", uniqueConstraints = {
        @UniqueConstraint(name = "uq_documento_requisito", columnNames = { "documento_id", "requisito_id" })
})
public class DocumentoRequisito {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "documento_id", nullable = false)
    private UUID documentoId;

    @Column(name = "requisito_id", nullable = false)
    private UUID requisitoId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public DocumentoRequisito() {
    }

    @PrePersist
    protected void antesDeGuardar() {
        if (id == null) {
            id = UUID.randomUUID();
        }

        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentoId() {
        return documentoId;
    }

    public void setDocumentoId(UUID documentoId) {
        this.documentoId = documentoId;
    }

    public UUID getRequisitoId() {
        return requisitoId;
    }

    public void setRequisitoId(UUID requisitoId) {
        this.requisitoId = requisitoId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
