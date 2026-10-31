package cl.legga.documentos.documento.validation;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

@Component
public class DocumentoValidator {

    private static final long MAX_SIZE = 10L * 1024 * 1024;

    public String validar(MultipartFile archivo) throws IOException {

        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("El archivo está vacío.");
        }

        if (archivo.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "El archivo supera el tamaño máximo permitido de 10 MB.");
        }

        String nombre = archivo.getOriginalFilename();

        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException(
                    "El archivo debe tener un nombre válido.");
        }

        String extension = nombre.substring(
                nombre.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);

        byte[] contenido = archivo.getBytes();

        if (extension.equals("pdf") && esPdf(contenido)) {
            return "application/pdf";
        }

        if ((extension.equals("jpg") || extension.equals("jpeg"))
                && esJpeg(contenido)) {
            return "image/jpeg";
        }

        if (extension.equals("png") && esPng(contenido)) {
            return "image/png";
        }

        throw new IllegalArgumentException(
                "Formato no permitido o contenido incompatible. "
                        + "Solo se aceptan PDF, JPG y PNG.");
    }

    private boolean esPdf(byte[] bytes) {
        return bytes.length >= 5
                && bytes[0] == '%'
                && bytes[1] == 'P'
                && bytes[2] == 'D'
                && bytes[3] == 'F'
                && bytes[4] == '-';
    }

    private boolean esJpeg(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    private boolean esPng(byte[] bytes) {
        return bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A;
    }
}