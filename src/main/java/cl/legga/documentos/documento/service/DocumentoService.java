package cl.legga.documentos.documento.service;

// Entidad y repositorio de documentos.
import cl.legga.documentos.documento.entity.Documento;
import cl.legga.documentos.documento.repository.DocumentoRepository;

// Servicio que registra eventos en la tabla Outbox.
import cl.legga.documentos.outbox.OutboxEventService;

// Jackson permite convertir el payload del evento a JSON.
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

// Anotaciones de Spring para servicio y transacciones.
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Utilidades de Java.
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio de aplicación para gestionar los metadatos documentales.
 *
 * Responsabilidades:
 * - Consultar documentos.
 * - Guardar documentos.
 * - Desvincular documentos.
 * - Actualizar el nombre visible.
 * - Registrar eventos Outbox cuando se carga un documento.
 */
@Service
public class DocumentoService {

    // Repositorio que persiste los metadatos en PostgreSQL.
    private final DocumentoRepository documentoRepository;

    // Servicio que registra los eventos en la tabla Outbox.
    private final OutboxEventService outboxEventService;

    // Componente que convierte objetos Java a JSON.
    private final ObjectMapper objectMapper;

    /**
     * Constructor con inyección de dependencias.
     */
    public DocumentoService(
            DocumentoRepository documentoRepository,
            OutboxEventService outboxEventService,
            ObjectMapper objectMapper) {

        this.documentoRepository = documentoRepository;
        this.outboxEventService = outboxEventService;
        this.objectMapper = objectMapper;
    }

    /**
     * Obtiene todos los documentos registrados.
     */
    @Transactional(readOnly = true)
    public List<Documento> listarTodos() {
        return documentoRepository.findAll();
    }

    /**
     * Busca un documento por su identificador.
     */
    @Transactional(readOnly = true)
    public Optional<Documento> buscarPorId(UUID id) {
        return documentoRepository.findById(id);
    }

    /**
     * Busca los documentos asociados a un legajo.
     */
    @Transactional(readOnly = true)
    public List<Documento> buscarPorLegajoId(UUID legajoId) {
        return documentoRepository.findByLegajoId(legajoId);
    }

    /**
     * Guarda un documento sin emitir un evento de carga.
     *
     * Este método se conserva para operaciones que no representan
     * necesariamente una nueva carga documental.
     */
    @Transactional
    public Documento guardar(Documento documento) {
        return documentoRepository.save(documento);
    }

    /**
     * Guarda los metadatos de un documento recién cargado y registra
     * el evento Outbox dentro de la misma transacción de PostgreSQL.
     *
     * Si falla el registro del evento, la transacción se revierte
     * y no queda persistido el documento sin su evento correspondiente.
     *
     * @param documento documento cuyos metadatos se guardarán.
     * @param actorId   identificador del usuario que realiza la carga.
     * @return documento persistido.
     */
    @Transactional
    public Documento guardarDocumentoCargado(
            Documento documento,
            String actorId) {

        // 1. Persistir los metadatos del documento.
        Documento documentoGuardado = documentoRepository.save(documento);

        // 2. Construir el payload de auditoría.
        // Se utiliza LinkedHashMap para mantener un orden legible
        // de las propiedades al serializar el JSON.
        Map<String, Object> payload = new LinkedHashMap<>();

        // Identificador del documento creado.
        payload.put(
                "documentoId",
                documentoGuardado.getId().toString());

        // Identificador del legajo al que quedó asociado.
        payload.put(
                "legajoId",
                documentoGuardado.getLegajoId().toString());

        // Usuario que ejecutó la carga.
        payload.put("actorId", actorId);

        // Estado documental posterior a la carga.
        payload.put("estado", documentoGuardado.getEstado());

        // Nombre original del archivo.
        payload.put(
                "nombreOriginal",
                documentoGuardado.getNombreOriginal());

        // Tipo MIME validado por el servicio de validación.
        payload.put(
                "tipoMime",
                documentoGuardado.getTipoMime());

        // Tamaño del archivo en bytes.
        payload.put(
                "tamanoBytes",
                documentoGuardado.getTamanoBytes());

        // Fecha y hora de creación del documento.
        payload.put(
                "occurredAt",
                documentoGuardado.getCreatedAt().toString());

        // 3. Convertir el payload a JSON.
        final String payloadJson;

        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {

            // Lanzar una excepción provoca la reversión
            // de la transacción de PostgreSQL.
            throw new IllegalStateException(
                    "No fue posible construir el payload del evento Outbox.",
                    ex);
        }

        // 4. Registrar el evento Outbox.
        // La llamada participa en la transacción actual.
        outboxEventService.registrarEvento(
                "Legga.Documentos.Cargado",
                "Documento",
                documentoGuardado.getId(),
                payloadJson);

        // 5. Devolver la entidad persistida.
        return documentoGuardado;
    }

    /**
     * Desvincula un documento de su legajo.
     *
     * Este método actualiza el estado documental.
     * En este paso todavía no estamos agregando el evento
     * Legga.Documentos.Desvinculado.
     */
    @Transactional
    public Optional<Documento> desvincular(UUID id) {

        return documentoRepository.findById(id)
                .map(documento -> {

                    // Cambiar el estado del documento.
                    documento.setEstado("DESVINCULADO");

                    // Actualizar la fecha de modificación.
                    documento.setUpdatedAt(OffsetDateTime.now());

                    // Persistir el cambio.
                    return documentoRepository.save(documento);
                });
    }

    /**
     * Actualiza el nombre visible de un documento.
     */
    @Transactional
    public Optional<Documento> actualizarNombreVisible(
            UUID id,
            String nombreVisible) {

        // Validar que el nombre recibido tenga contenido.
        if (nombreVisible == null || nombreVisible.isBlank()) {
            throw new IllegalArgumentException(
                    "El nombre visible no puede estar vacío");
        }

        // Validar el largo máximo permitido.
        if (nombreVisible.length() > 255) {
            throw new IllegalArgumentException(
                    "El nombre visible no puede superar los 255 caracteres");
        }

        // Buscar el documento y actualizarlo si existe.
        return documentoRepository.findById(id)
                .map(documento -> {

                    // Guardar el nombre sin espacios externos.
                    documento.setNombreVisible(nombreVisible.trim());

                    // Actualizar la fecha de modificación.
                    documento.setUpdatedAt(OffsetDateTime.now());

                    // Persistir el cambio.
                    return documentoRepository.save(documento);
                });
    }
}