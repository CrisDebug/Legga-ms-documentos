
package cl.legga.documentos.documento.repository;

import cl.legga.documentos.documento.entity.DocumentoRequisito;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentoRequisitoRepository
        extends JpaRepository<DocumentoRequisito, UUID> {

    List<DocumentoRequisito> findByRequisitoId(UUID requisitoId);

    List<DocumentoRequisito> findByDocumentoId(UUID documentoId);

    boolean existsByDocumentoIdAndRequisitoId(
            UUID documentoId,
            UUID requisitoId);
}
