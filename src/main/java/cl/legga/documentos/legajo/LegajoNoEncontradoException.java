package cl.legga.documentos.legajo;

/**
 * Excepción utilizada cuando MS-Legajos confirma
 * que el legajo solicitado no existe.
 */
public class LegajoNoEncontradoException extends RuntimeException {

    public LegajoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}