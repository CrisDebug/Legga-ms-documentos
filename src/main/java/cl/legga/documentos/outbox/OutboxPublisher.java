package cl.legga.documentos.outbox;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.util.BinaryData;
import com.azure.messaging.eventgrid.EventGridEvent;
import com.azure.messaging.eventgrid.EventGridPublisherClient;
import com.azure.messaging.eventgrid.EventGridPublisherClientBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Publisher encargado de publicar en Azure Event Grid
 * los eventos registrados en la tabla Outbox.
 *
 * El proceso:
 * 1. Reclama un lote de eventos disponibles.
 * 2. Construye un evento compatible con Azure Event Grid.
 * 3. Publica el evento.
 * 4. Marca el evento como PUBLISHED si el envío fue aceptado.
 * 5. Registra el fallo si ocurre un error.
 *
 * Este proceso se ejecuta automáticamente mediante @Scheduled.
 */
@Component
public class OutboxPublisher {

    /**
     * Logger para registrar información operativa.
     *
     * No se debe registrar la clave de Event Grid ni el payload completo.
     */
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    /**
     * Cantidad máxima de eventos que se procesan por ejecución.
     */
    private static final int LIMITE_LOTE = 50;

    /**
     * Servicio encargado de administrar los estados de los eventos Outbox.
     */
    private final OutboxEventService outboxEventService;

    /**
     * Cliente oficial de Azure Event Grid.
     *
     * Se construye una sola vez cuando Spring crea este componente.
     */
    private final EventGridPublisherClient<EventGridEvent> eventGridClient;

    /**
     * Constructor del publisher.
     *
     * Spring obtiene el endpoint y la clave desde application.properties,
     * que a su vez utiliza variables de entorno.
     *
     * @param outboxEventService Servicio de administración del Outbox.
     * @param endpoint           Endpoint del tópico de Azure Event Grid.
     * @param key                Clave de autenticación de Event Grid.
     */
    public OutboxPublisher(
            OutboxEventService outboxEventService,
            @Value("${azure.eventgrid.endpoint:}") String endpoint,
            @Value("${azure.eventgrid.key:}") String key) {

        this.outboxEventService = outboxEventService;

        /*
         * Validamos que las credenciales estén configuradas.
         *
         * Evitamos construir un cliente con valores vacíos.
         */
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException(
                    "No está configurado AZURE_EVENTGRID_ENDPOINT");
        }

        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "No está configurado AZURE_EVENTGRID_KEY");
        }

        /*
         * Construimos el cliente de Azure Event Grid.
         *
         * La clave se obtiene desde una variable de entorno.
         * Nunca debe escribirse directamente en el código fuente.
         */
        this.eventGridClient = new EventGridPublisherClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(key))
                .buildEventGridEventPublisherClient();
    }

    /**
     * Ejecuta periódicamente la publicación de eventos pendientes.
     *
     * El intervalo predeterminado es de 60 segundos.
     *
     * Puede modificarse mediante la propiedad:
     *
     * outbox.publisher.delay-ms
     */
    @Scheduled(fixedDelayString = "${outbox.publisher.delay-ms:60000}")
    public void publicarPendientes() {

        /*
         * Reclamamos un lote de eventos.
         *
         * El servicio marca los registros como PROCESSING
         * antes de devolverlos.
         */
        List<OutboxEvent> eventos = outboxEventService.reclamarEventosPendientes(LIMITE_LOTE);

        /*
         * Si no hay eventos disponibles, no realizamos trabajo adicional.
         */
        if (eventos.isEmpty()) {
            return;
        }

        log.info(
                "Se reclamarán {} eventos Outbox para publicación",
                eventos.size());

        /*
         * Procesamos cada evento de forma independiente.
         *
         * Un error en un evento no impide procesar los siguientes.
         */
        for (OutboxEvent evento : eventos) {

            try {

                /*
                 * Construimos el evento que será enviado a Azure Event Grid.
                 *
                 * Subject:
                 * Identifica el agregado relacionado con el evento.
                 *
                 * EventType:
                 * Tipo de evento de negocio, por ejemplo:
                 * Legga.Documentos.Cargado.
                 *
                 * Data:
                 * Payload JSON almacenado en el Outbox.
                 *
                 * DataVersion:
                 * Versión del contrato del payload.
                 */
                EventGridEvent eventGridEvent = new EventGridEvent(
                        "/legga/documentos/" + evento.getAggregateId(),
                        evento.getEventType(),
                        BinaryData.fromString(evento.getPayload()),
                        "1.0")
                        .setId(evento.getEventId().toString())
                        .setEventTime(evento.getOccurredAt());

                /*
                 * Enviamos el evento a Azure Event Grid.
                 *
                 * Si el envío falla, el cliente lanza una excepción
                 * y el evento no se marca como publicado.
                 */
                eventGridClient.sendEvent(eventGridEvent);

                /*
                 * Marcamos el evento como publicado únicamente
                 * después de que sendEvent retorna correctamente.
                 */
                outboxEventService.marcarPublicado(
                        evento.getEventId());

                log.info(
                        "Evento Outbox publicado. eventId={}, eventType={}",
                        evento.getEventId(),
                        evento.getEventType());

            } catch (Exception ex) {

                /*
                 * Registramos el fallo.
                 *
                 * El servicio incrementa attempts y determina si el evento
                 * vuelve a PENDING o queda en FAILED.
                 *
                 * No guardamos el payload ni las credenciales en los logs.
                 */
                String mensajeError = obtenerMensajeError(ex);

                log.error(
                        "Falló la publicación del evento Outbox. "
                                + "eventId={}, eventType={}, error={}",
                        evento.getEventId(),
                        evento.getEventType(),
                        mensajeError);

                /*
                 * Registramos el fallo en PostgreSQL.
                 *
                 * Si este registro también falla, la excepción se propaga
                 * y quedará disponible para los logs de Spring.
                 */
                outboxEventService.registrarFallo(
                        evento.getEventId(),
                        mensajeError);
            }
        }
    }

    /**
     * Obtiene un mensaje de error seguro y no vacío.
     *
     * El mensaje se utiliza para el registro operativo del Outbox.
     *
     * @param ex Excepción producida durante la publicación.
     * @return Mensaje descriptivo del error.
     */
    private String obtenerMensajeError(Exception ex) {

        if (ex.getMessage() == null || ex.getMessage().isBlank()) {
            return "Error desconocido al publicar evento en Event Grid";
        }

        return ex.getMessage();
    }
}