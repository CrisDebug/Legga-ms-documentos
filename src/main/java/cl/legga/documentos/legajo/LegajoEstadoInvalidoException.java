package cl.legga.documentos.legajo;

/**
 * Excepción utilizada cuando el legajo existe,
 * pero no se encuentra en estado ACTIVO.
 */
public class LegajoEstadoInvalidoException extends RuntimeException {

    public LegajoEstadoInvalidoException(String mensaje) {
        super(mensaje);
    }
}