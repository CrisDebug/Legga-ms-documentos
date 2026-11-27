
package cl.legga.documentos.documento.service;

import cl.legga.documentos.documento.entity.Documento;
import cl.legga.documentos.documento.entity.DocumentoRequisito;
import cl.legga.documentos.documento.repository.DocumentoRepository;
import cl.legga.documentos.documento.repository.DocumentoRequisitoRepository;
import cl.legga.documentos.outbox.OutboxEventService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class DocumentoService {

        private final DocumentoRepository documentoRepository;
        private final DocumentoRequisitoRepository documentoRequisitoRepository;
        private final OutboxEventService outboxEventService;
        private final ObjectMapper objectMapper;

        public DocumentoService(
                        DocumentoRepository documentoRepository,
                        DocumentoRequisitoRepository documentoRequisitoRepository,
                        OutboxEventService outboxEventService,
                        ObjectMapper objectMapper) {

                this.documentoRepository = documentoRepository;
                this.documentoRequisitoRepository = documentoRequisitoRepository;
                this.outboxEventService = outboxEventService;
                this.objectMapper = objectMapper;
        }

        @Transactional(readOnly = true)
        public List<Documento> listarTodos() {
                return documentoRepository.findAll();
        }

        @Transactional(readOnly = true)
        public Optional<Documento> buscarPorId(UUID id) {
                return documentoRepository.findById(id);
        }

        @Transactional(readOnly = true)
        public List<Documento> buscarPorLegajoId(UUID legajoId) {
                return documentoRepository.findByLegajoId(legajoId);
        }

        /**
         * Busca los documentos asociados a un requisito documental.
         */
        @Transactional(readOnly = true)
        public List<Documento> buscarPorRequisitoId(UUID requisitoId) {

                if (requisitoId == null) {
                        throw new IllegalArgumentException(
                                        "El requisito documental es obligatorio.");
                }

                return documentoRequisitoRepository.findByRequisitoId(requisitoId)
                                .stream()
                                .map(DocumentoRequisito::getDocumentoId)
                                .map(documentoRepository::findById)
                                .flatMap(Optional::stream)
                                .filter(documento -> "ASOCIADO".equals(documento.getEstado()))
                                .toList();
        }

        /**
         * Recupera todos los requisitos asociados a un documento.
         */
        @Transactional(readOnly = true)
        public List<UUID> obtenerRequisitoIds(UUID documentoId) {

                if (documentoId == null) {
                        throw new IllegalArgumentException(
                                        "El documento es obligatorio.");
                }

                return documentoRequisitoRepository.findByDocumentoId(documentoId)
                                .stream()
                                .map(DocumentoRequisito::getRequisitoId)
                                .toList();
        }

        @Transactional
        public Documento guardar(Documento documento) {
                return documentoRepository.save(documento);
        }

        @Transactional
        public Documento guardarDocumentoCargado(
                        Documento documento,
                        UUID requisitoId,
                        String actorId) {

                if (documento == null) {
                        throw new IllegalArgumentException(
                                        "El documento es obligatorio.");
                }

                if (requisitoId == null) {
                        throw new IllegalArgumentException(
                                        "El requisito documental es obligatorio.");
                }

                if (documento.getInstitucionId() == null) {
                        throw new IllegalArgumentException(
                                        "La institución del documento es obligatoria.");
                }

                if (documento.getLegajoId() == null) {
                        throw new IllegalArgumentException(
                                        "El legajo del documento es obligatorio.");
                }

                if (actorId == null || actorId.isBlank()) {
                        throw new IllegalArgumentException(
                                        "El usuario que realiza la carga es obligatorio.");
                }

                Documento documentoGuardado = documentoRepository.save(documento);

                DocumentoRequisito asociacion = new DocumentoRequisito();
                asociacion.setDocumentoId(documentoGuardado.getId());
                asociacion.setRequisitoId(requisitoId);

                documentoRequisitoRepository.save(asociacion);

                Map<String, Object> payload = new LinkedHashMap<>();

                payload.put("documentoId", documentoGuardado.getId().toString());
                payload.put("legajoId", documentoGuardado.getLegajoId().toString());
                payload.put("institucionId", documentoGuardado.getInstitucionId().toString());
                payload.put("requisitoId", requisitoId.toString());
                payload.put("actorId", actorId);
                payload.put("estado", documentoGuardado.getEstado());
                payload.put("nombreOriginal", documentoGuardado.getNombreOriginal());
                payload.put("tipoMime", documentoGuardado.getTipoMime());
                payload.put("tamanoBytes", documentoGuardado.getTamanoBytes());
                payload.put("occurredAt", documentoGuardado.getCreatedAt().toString());

                final String payloadJson;

                try {
                        payloadJson = objectMapper.writeValueAsString(payload);
                } catch (JsonProcessingException ex) {
                        throw new IllegalStateException(
                                        "No fue posible construir el payload del evento Outbox.",
                                        ex);
                }

                outboxEventService.registrarEvento(
                                "Legga.Documentos.Cargado",
                                "Documento",
                                documentoGuardado.getId(),
                                payloadJson);

                return documentoGuardado;
        }

        @Transactional
        public Optional<Documento> desvincular(UUID id) {

                return documentoRepository.findById(id)
                                .map(documento -> {

                                        documento.setEstado("DESVINCULADO");
                                        documento.setUpdatedAt(OffsetDateTime.now());

                                        return documentoRepository.save(documento);
                                });
        }

        @Transactional
        public Optional<Documento> actualizarNombreVisible(
                        UUID id,
                        String nombreVisible) {

                if (nombreVisible == null || nombreVisible.isBlank()) {
                        throw new IllegalArgumentException(
                                        "El nombre visible no puede estar vacío.");
                }

                if (nombreVisible.length() > 255) {
                        throw new IllegalArgumentException(
                                        "El nombre visible no puede superar los 255 caracteres.");
                }

                return documentoRepository.findById(id)
                                .map(documento -> {

                                        documento.setNombreVisible(nombreVisible.trim());
                                        documento.setUpdatedAt(OffsetDateTime.now());

                                        return documentoRepository.save(documento);
                                });
        }
}
