package cl.legga.documentos.legajo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Cliente HTTP responsable de consultar MS-Legajos.
 *
 * No accede directamente a la base de datos de MS-Legajos.
 * Utiliza su API REST para verificar la existencia y estado del legajo.
 */
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
     * Consulta el legajo y verifica que su estado sea ACTIVO.
     *
     * @param legajoId identificador del legajo.
     * @return true si el legajo existe y está ACTIVO.
     */
    public boolean existeLegajo(String legajoId) {

        try {
            LegajoResponse legajo = restClient.get()
                    .uri("/api/legajos/{id}", legajoId)
                    .retrieve()
                    .body(LegajoResponse.class);

            if (legajo == null) {
                throw new LegajoServiceException(
                        "MS-Legajos devolvió una respuesta vacía.", null);
            }

            if (!"ACTIVO".equalsIgnoreCase(legajo.estado())) {
                throw new LegajoEstadoInvalidoException(
                        "El legajo no se encuentra ACTIVO.");
            }

            return true;

        } catch (RestClientResponseException ex) {

            if (ex.getStatusCode().value() == 404) {
                throw new LegajoNoEncontradoException(
                        "El legajo indicado no existe.");
            }

            throw new LegajoServiceException(
                    "MS-Legajos respondió con un error.", ex);

        } catch (LegajoNoEncontradoException
                | LegajoEstadoInvalidoException
                | LegajoServiceException ex) {

            throw ex;

        } catch (Exception ex) {

            throw new LegajoServiceException(
                    "No fue posible comunicarse con MS-Legajos.", ex);
        }
    }

    /**
     * DTO mínimo para leer la respuesta de MS-Legajos.
     */
    private record LegajoResponse(String estado) {
    }
}