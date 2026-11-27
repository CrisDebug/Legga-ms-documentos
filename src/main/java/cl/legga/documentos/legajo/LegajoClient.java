
package cl.legga.documentos.legajo;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class LegajoClient {

        private final RestClient restClient;

        public LegajoClient(
                        RestClient.Builder restClientBuilder,
                        @Value("${legga.ms-legajos.base-url}") String baseUrl) {

                this.restClient = restClientBuilder
                                .baseUrl(baseUrl)
                                .build();
        }

        /**
         * Obtiene la institución propietaria de un legajo.
         */
        public UUID obtenerInstitucionId(
                        UUID legajoId,
                        String tenantId,
                        String objectId) {

                LegajoResponse legajo = consultarLegajo(
                                legajoId,
                                tenantId,
                                objectId);

                if (legajo.institucionId() == null) {
                        throw new LegajoServiceException(
                                        "MS-Legajos no devolvió el identificador de institución.",
                                        null);
                }

                return legajo.institucionId();
        }

        /**
         * Valida que el requisito exista, esté activo
         * y pertenezca a la categoría del legajo.
         */
        public void validarRequisito(
                        UUID legajoId,
                        UUID requisitoId,
                        String tenantId,
                        String objectId) {

                if (requisitoId == null) {
                        throw new IllegalArgumentException(
                                        "El identificador del requisito es obligatorio.");
                }

                LegajoResponse legajo = consultarLegajo(
                                legajoId,
                                tenantId,
                                objectId);

                if (legajo.categoriaId() == null) {
                        throw new LegajoServiceException(
                                        "MS-Legajos no devolvió la categoría del legajo.",
                                        null);
                }

                try {

                        List<RequisitoResponse> requisitos = restClient.get()
                                        .uri(
                                                        "/api/categorias/{categoriaId}/requisitos-documentales",
                                                        legajo.categoriaId())
                                        .header("X-LEGGA-TENANT-ID", tenantId)
                                        .header("X-LEGGA-OBJECT-ID", objectId)
                                        .retrieve()
                                        .body(new ParameterizedTypeReference<List<RequisitoResponse>>() {
                                        });

                        if (requisitos == null) {
                                throw new LegajoServiceException(
                                                "MS-Legajos devolvió una lista de requisitos vacía.",
                                                null);
                        }

                        boolean requisitoValido = requisitos.stream()
                                        .anyMatch(requisito -> requisitoId.equals(requisito.id())
                                                        && Boolean.TRUE.equals(requisito.activo())
                                                        && legajo.categoriaId().equals(
                                                                        requisito.categoriaId()));

                        if (!requisitoValido) {
                                throw new IllegalArgumentException(
                                                "El requisito no existe, está inactivo "
                                                                + "o no pertenece a la categoría del legajo.");
                        }

                } catch (RestClientResponseException ex) {

                        throw new LegajoServiceException(
                                        "No fue posible consultar los requisitos documentales. "
                                                        + "Código HTTP: "
                                                        + ex.getStatusCode().value(),
                                        ex);

                } catch (IllegalArgumentException | LegajoServiceException ex) {

                        throw ex;

                } catch (Exception ex) {

                        throw new LegajoServiceException(
                                        "No fue posible comunicarse con MS-Legajos "
                                                        + "para validar el requisito.",
                                        ex);
                }
        }

        /**
         * Consulta el legajo enviando los encabezados de identidad.
         */
        private LegajoResponse consultarLegajo(
                        UUID legajoId,
                        String tenantId,
                        String objectId) {

                if (legajoId == null) {
                        throw new IllegalArgumentException(
                                        "El identificador del legajo es obligatorio.");
                }

                if (tenantId == null || tenantId.isBlank()) {
                        throw new IllegalArgumentException(
                                        "El tenant de identidad es obligatorio.");
                }

                if (objectId == null || objectId.isBlank()) {
                        throw new IllegalArgumentException(
                                        "El identificador del usuario es obligatorio.");
                }

                try {

                        LegajoResponse legajo = restClient.get()
                                        .uri("/api/legajos/{id}", legajoId)
                                        .header("X-LEGGA-TENANT-ID", tenantId)
                                        .header("X-LEGGA-OBJECT-ID", objectId)
                                        .retrieve()
                                        .body(LegajoResponse.class);

                        if (legajo == null) {
                                throw new LegajoServiceException(
                                                "MS-Legajos devolvió una respuesta vacía.",
                                                null);
                        }

                        if (!"ACTIVO".equalsIgnoreCase(legajo.estado())) {
                                throw new LegajoEstadoInvalidoException(
                                                "El legajo no se encuentra ACTIVO.");
                        }

                        return legajo;

                } catch (RestClientResponseException ex) {

                        if (ex.getStatusCode().value() == 404) {
                                throw new LegajoNoEncontradoException(
                                                "El legajo indicado no existe.");
                        }

                        throw new LegajoServiceException(
                                        "MS-Legajos respondió con un error. Código HTTP: "
                                                        + ex.getStatusCode().value(),
                                        ex);

                } catch (LegajoNoEncontradoException
                                | LegajoEstadoInvalidoException
                                | LegajoServiceException ex) {

                        throw ex;

                } catch (Exception ex) {

                        throw new LegajoServiceException(
                                        "No fue posible comunicarse con MS-Legajos.",
                                        ex);
                }
        }

        /**
         * Respuesta del endpoint de legajos.
         */
        private record LegajoResponse(
                        String estado,
                        UUID institucionId,
                        UUID categoriaId) {
        }

        /**
         * Respuesta del endpoint de requisitos documentales.
         */
        private record RequisitoResponse(
                        UUID id,
                        UUID categoriaId,
                        Boolean activo) {
        }
}
