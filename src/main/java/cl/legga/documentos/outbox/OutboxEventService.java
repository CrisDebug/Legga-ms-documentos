package cl.legga.documentos.outbox;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Servicio encargado de administrar los eventos del patrón Outbox.
 *
 * Responsabilidades:
 * - Registrar eventos en la tabla outbox_event.
 * - Reclamar eventos pendientes para su publicación.
 * - Marcar eventos como publicados.
 * - Registrar fallos y controlar la cantidad de reintentos.
 */
@Service
public class OutboxEventService {

    /**
     * Cantidad máxima de intentos permitidos para publicar un evento.
     *
     * Si un evento alcanza este límite, quedará en estado FAILED.
     */
    private static final int MAX_INTENTOS = 5;

    /**
     * Repositorio que permite consultar y persistir los eventos Outbox.
     */
    private final OutboxEventRepository outboxEventRepository;

    /**
     * Constructor utilizado por Spring para inyectar el repositorio.
     */
    public OutboxEventService(
            OutboxEventRepository outboxEventRepository) {

        this.outboxEventRepository = outboxEventRepository;
    }

    /**
     * Registra un nuevo evento en la tabla documentos.outbox_event.
     *
     * Este método debe ejecutarse dentro de la misma transacción
     * que la operación de negocio que genera el evento.
     *
     * De esta forma, el cambio de negocio y el registro del evento
     * pueden confirmarse o revertirse conjuntamente.
     *
     * @param eventType    Tipo de evento, por ejemplo Legga.Documentos.Cargado.
     * @param aggregateType Tipo de entidad que genera el evento.
     * @param aggregateId   Identificador de la entidad relacionada.
     * @param payloadJson   Contenido JSON del evento.
     */
    @Transactional
    public void registrarEvento(
            String eventType,
            String aggregateType,
            UUID aggregateId,
            String payloadJson) {

        // Construimos la entidad Outbox con los datos recibidos.
        OutboxEvent event = new OutboxEvent(
                eventType,
                aggregateType,
                aggregateId,
                payloadJson);

        // Persistimos el evento en PostgreSQL.
        outboxEventRepository.save(event);
    }

    /**
     * Reclama un lote de eventos que pueden ser publicados.
     *
     * El repositorio selecciona:
     * - Eventos PENDING.
     * - Eventos PROCESSING abandonados por más de 10 minutos.
     *
     * El repositorio utiliza FOR UPDATE SKIP LOCKED para evitar
     * que dos instancias reclamen simultáneamente los mismos registros.
     *
     * @param limite Cantidad máxima de eventos que se reclamarán.
     * @return Lista de eventos marcados como PROCESSING.
     */
    @Transactional
    public List<OutboxEvent> reclamarEventosPendientes(int limite) {

        // Validamos que el tamaño del lote sea positivo.
        if (limite <= 0) {
            throw new IllegalArgumentException(
                    "El límite debe ser mayor que cero");
        }

        /*
         * Recuperamos los eventos disponibles.
         *
         * La consulta ya contempla los eventos PENDING y los PROCESSING
         * que llevan más de 10 minutos sin actualizarse.
         *
         * La firma del repositorio ahora recibe únicamente el límite.
         */
        List<OutboxEvent> lote =
                outboxEventRepository.reclamarPendientes(limite);

        /*
         * Marcamos todos los eventos reclamados como PROCESSING.
         *
         * Este cambio registra el momento en que comienza el procesamiento.
         */
        lote.forEach(OutboxEvent::marcarEnProceso);

        /*
         * Guardamos los cambios de estado en la base de datos.
         *
         * La transacción se confirma al finalizar el método.
         */
        return outboxEventRepository.saveAll(lote);
    }

    /**
     * Marca un evento como publicado correctamente.
     *
     * @param eventId Identificador del evento.
     */
    @Transactional
    public void marcarPublicado(UUID eventId) {

        // Buscamos el evento en la base de datos.
        OutboxEvent event = obtenerEvento(eventId);

        // Actualizamos su estado a PUBLISHED.
        event.marcarPublicado();

        // Persistimos el cambio.
        outboxEventRepository.save(event);
    }

    /**
     * Registra un error ocurrido durante la publicación.
     *
     * El evento vuelve a PENDING mientras no alcance el máximo
     * de intentos. Al alcanzar el límite, queda en FAILED.
     *
     * @param eventId Identificador del evento.
     * @param error   Mensaje descriptivo del error.
     */
    @Transactional
    public void registrarFallo(UUID eventId, String error) {

        // Buscamos el evento que falló.
        OutboxEvent event = obtenerEvento(eventId);

        // Incrementamos el contador y actualizamos su estado.
        event.registrarError(error, MAX_INTENTOS);

        // Persistimos el resultado del intento.
        outboxEventRepository.save(event);
    }

    /**
     * Obtiene los eventos que actualmente están pendientes.
     *
     * Este método es de consulta y no modifica la base de datos.
     *
     * @return Lista de eventos con estado PENDING.
     */
    @Transactional(readOnly = true)
    public List<OutboxEvent> obtenerEventosPendientes() {

        // Consultamos los eventos y filtramos los que están pendientes.
        return outboxEventRepository.findAll()
                .stream()
                .filter(event -> "PENDING".equals(event.getStatus()))
                .toList();
    }

    /**
     * Busca un evento por su identificador.
     *
     * Si no existe, lanza una excepción para indicar que no se
     * puede actualizar un evento inexistente.
     *
     * @param eventId Identificador del evento.
     * @return Evento encontrado.
     */
    private OutboxEvent obtenerEvento(UUID eventId) {

        return outboxEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el evento Outbox: " + eventId));
    }
}