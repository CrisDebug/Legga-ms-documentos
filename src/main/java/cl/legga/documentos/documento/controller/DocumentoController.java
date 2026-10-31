
package cl.legga.documentos.documento.controller;

import cl.legga.documentos.documento.dto.DocumentoResponse;
import cl.legga.documentos.documento.entity.Documento;
import cl.legga.documentos.documento.service.DocumentoService;
import cl.legga.documentos.storage.DocumentoStorageService;

import cl.legga.documentos.legajo.LegajoEstadoInvalidoException;
import cl.legga.documentos.legajo.LegajoNoEncontradoException;
import cl.legga.documentos.legajo.LegajoServiceException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controlador REST para la gestión documental de LEGGA.
 *
 * Expone operaciones para:
 * - Cargar documentos.
 * - Consultar documentos.
 * - Consultar documentos asociados a un legajo.
 * - Desvincular documentos.
 *
 * La autorización de usuarios y la validación de existencia
 * del legajo deberán integrarse antes de habilitar el servicio
 * en un entorno productivo.
 */
@RestController
@RequestMapping("/api/documentos")
public class DocumentoController {

        private final DocumentoService documentoService;
        private final DocumentoStorageService documentoStorageService;

        /**
         * Constructor con inyección de dependencias.
         */
        public DocumentoController(
                        DocumentoService documentoService,
                        DocumentoStorageService documentoStorageService) {

                this.documentoService = documentoService;
                this.documentoStorageService = documentoStorageService;
        }

        /**
         * Carga un documento y registra sus metadatos.
         *
         * Endpoint:
         * POST /api/documentos
         *
         * Content-Type:
         * multipart/form-data
         *
         * Parámetros:
         * archivo: archivo PDF, JPG o PNG.
         * legajoId: UUID del legajo asociado.
         * uploadedBy: identificador temporal del usuario.
         *
         * IMPORTANTE:
         * uploadedBy es temporal para pruebas con Postman.
         * En la integración definitiva se obtendrá de la identidad
         * autenticada y no de un parámetro enviado por el cliente.
         *
         * La validación de existencia y vigencia del legajo
         * mediante MS-Legajos está pendiente.
         */
        @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<DocumentoResponse> cargarDocumento(

                        @RequestParam("archivo") MultipartFile archivo,

                        @RequestParam("legajoId") UUID legajoId,

                        @RequestParam("uploadedBy") String uploadedBy) {

                // Ejecuta el proceso de validación, almacenamiento
                // en Azure Blob y persistencia de metadatos.
                Documento documento = documentoStorageService.cargarDocumento(
                                archivo,
                                legajoId,
                                uploadedBy);

                // Convierte la entidad en DTO para responder al cliente.
                DocumentoResponse respuesta = convertirAResponse(documento);

                // Retorna HTTP 201 cuando el documento fue creado.
                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .body(respuesta);
        }

        /**
         * Lista todos los documentos registrados.
         *
         * GET /api/documentos
         */
        @GetMapping
        public ResponseEntity<List<DocumentoResponse>> listarTodos() {

                List<DocumentoResponse> documentos = documentoService.listarTodos()
                                .stream()
                                .map(this::convertirAResponse)
                                .toList();

                return ResponseEntity.ok(documentos);
        }

        /**
         * Consulta un documento mediante su UUID.
         *
         * GET /api/documentos/{id}
         */
        @GetMapping("/{id}")
        public ResponseEntity<DocumentoResponse> buscarPorId(
                        @PathVariable UUID id) {

                return documentoService.buscarPorId(id)
                                .map(this::convertirAResponse)
                                .map(ResponseEntity::ok)
                                .orElse(ResponseEntity.notFound().build());
        }

        /**
         * Consulta los documentos asociados a un legajo.
         *
         * GET /api/documentos/legajo/{legajoId}
         */
        @GetMapping("/legajo/{legajoId}")
        public ResponseEntity<List<DocumentoResponse>> buscarPorLegajo(
                        @PathVariable UUID legajoId) {

                List<DocumentoResponse> documentos = documentoService.buscarPorLegajoId(legajoId)
                                .stream()
                                .map(this::convertirAResponse)
                                .toList();

                return ResponseEntity.ok(documentos);
        }

        /**
         * Desvincula un documento de su legajo.
         *
         * PATCH /api/documentos/{id}/desvincular
         *
         * Esta operación modifica el estado lógico del documento.
         * No elimina el archivo físico de Azure Blob Storage.
         */
        @PatchMapping("/{id}/desvincular")
        public ResponseEntity<DocumentoResponse> desvincular(
                        @PathVariable UUID id) {

                return documentoService.desvincular(id)
                                .map(this::convertirAResponse)
                                .map(ResponseEntity::ok)
                                .orElse(ResponseEntity.notFound().build());
        }

        /**
         * Convierte la entidad Documento en su DTO de respuesta.
         *
         * No expone storageKey al cliente.
         */
        private DocumentoResponse convertirAResponse(Documento documento) {

                return new DocumentoResponse(
                                documento.getId(),
                                documento.getLegajoId(),
                                documento.getNombreOriginal(),
                                documento.getNombreVisible(),
                                documento.getTipoMime(),
                                documento.getTamanoBytes(),
                                documento.getEstado(),
                                documento.getUploadedBy(),
                                documento.getCreatedAt(),
                                documento.getUpdatedAt());
        }

        /**
         * Maneja errores de validación de los datos recibidos.
         *
         * Retorna HTTP 400 Bad Request.
         */
        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<Map<String, String>> manejarErrorValidacion(
                        IllegalArgumentException ex) {

                return ResponseEntity.badRequest().body(
                                Map.of("error", ex.getMessage()));
        }

        /**
         * Maneja errores durante el almacenamiento o persistencia.
         *
         * Retorna HTTP 500 Internal Server Error.
         */
        @ExceptionHandler(IllegalStateException.class)
        public ResponseEntity<Map<String, String>> manejarErrorProceso(
                        IllegalStateException ex) {

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(Map.of("error", ex.getMessage()));
        }

        /**
         * Maneja el caso en que MS-Legajos confirma
         * que el legajo solicitado no existe.
         *
         * Retorna HTTP 404 Not Found.
         */
        @ExceptionHandler(LegajoNoEncontradoException.class)
        public ResponseEntity<Map<String, String>> manejarLegajoNoEncontrado(
                        LegajoNoEncontradoException ex) {

                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(Map.of("error", ex.getMessage()));
        }

        /**
         * Maneja errores de comunicación o respuestas inesperadas
         * de MS-Legajos.
         *
         * Retorna HTTP 503 Service Unavailable.
         */
        @ExceptionHandler(LegajoServiceException.class)
        public ResponseEntity<Map<String, String>> manejarErrorMSLegajos(
                        LegajoServiceException ex) {

                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                .body(Map.of("error", ex.getMessage()));
        }

        /**
         * Maneja el caso en que el legajo existe,
         * pero no se encuentra en estado ACTIVO.
         *
         * Retorna HTTP 400 Bad Request.
         */
        @ExceptionHandler(LegajoEstadoInvalidoException.class)
        public ResponseEntity<Map<String, String>> manejarEstadoLegajoInvalido(
                        LegajoEstadoInvalidoException ex) {

                return ResponseEntity.badRequest()
                                .body(Map.of("error", ex.getMessage()));
        }
}
