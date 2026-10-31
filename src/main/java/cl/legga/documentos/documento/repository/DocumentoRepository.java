package cl.legga.documentos.documento.repository;

import cl.legga.documentos.documento.entity.Documento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

public interface DocumentoRepository extends JpaRepository<Documento, UUID> {
    List<Documento> findByLegajoId(UUID legajoId);
}