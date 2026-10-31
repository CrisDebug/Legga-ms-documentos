package cl.legga.documentos.legajo;

/**
 * Excepción para errores de comunicación o respuestas
 * inesperadas de MS-Legajos.
 */
public class LegajoServiceException extends RuntimeException {

    public LegajoServiceException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}