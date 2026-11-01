package cl.legga.documentos.documento.service;

import cl.legga.documentos.documento.entity.Documento;
import cl.legga.documentos.documento.repository.DocumentoRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DocumentoService {

    private final DocumentoRepository documentoRepository;

    public DocumentoService(DocumentoRepository documentoRepository) {
        this.documentoRepository = documentoRepository;
    }

    public List<Documento> listarTodos() {
        return documentoRepository.findAll();
    }

    public Optional<Documento> buscarPorId(UUID id) {
        return documentoRepository.findById(id);
    }

    public List<Documento> buscarPorLegajoId(UUID legajoId) {
        return documentoRepository.findByLegajoId(legajoId);
    }

    public Documento guardar(Documento documento) {
        return documentoRepository.save(documento);
    }

    // desvincular un documento
    public Optional<Documento> desvincular(UUID id) {
        return documentoRepository.findById(id)
                .map(documento -> {
                    documento.setEstado("DESVINCULADO");
                    documento.setUpdatedAt(OffsetDateTime.now());
                    return documentoRepository.save(documento);
                });
    }

    // actualizar nombre
    public Optional<Documento> actualizarNombreVisible(
            UUID id,
            String nombreVisible) {

        if (nombreVisible == null || nombreVisible.isBlank()) {
            throw new IllegalArgumentException(
                    "El nombre visible no puede estar vacío");
        }

        if (nombreVisible.length() > 255) {
            throw new IllegalArgumentException(
                    "El nombre visible no puede superar los 255 caracteres");
        }

        return documentoRepository.findById(id)
                .map(documento -> {
                    documento.setNombreVisible(nombreVisible.trim());
                    documento.setUpdatedAt(OffsetDateTime.now());
                    return documentoRepository.save(documento);
                });
    }
}