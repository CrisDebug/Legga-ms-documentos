
package cl.legga.documentos.documento.controller;

import cl.legga.documentos.documento.dto.ActualizarNombreDocumentoRequest;
import cl.legga.documentos.documento.dto.DocumentoResponse;
import cl.legga.documentos.documento.entity.Documento;
import cl.legga.documentos.documento.service.DocumentoService;
import cl.legga.documentos.storage.DocumentoStorageService;
import cl.legga.documentos.legajo.LegajoEstadoInvalidoException;
import cl.legga.documentos.legajo.LegajoNoEncontradoException;
import cl.legga.documentos.legajo.LegajoServiceException;
import cl.legga.documentos.storage.AzureBlobService;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documentos")
public class DocumentoController {

        private final DocumentoService documentoService;
        private final DocumentoStorageService documentoStorageService;
        private final AzureBlobService azureBlobService;

        public DocumentoController(
                        DocumentoService documentoService,
                        DocumentoStorageService documentoStorageService,
                        AzureBlobService azureBlobService) {

                this.documentoService = documentoService;
                this.documentoStorageService = documentoStorageService;
                this.azureBlobService = azureBlobService;
        }

        /**
         * Carga un documento y lo asocia a un requisito documental.
         *
         * POST /api/documentos
         */
        @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<DocumentoResponse> cargarDocumento(

                        @RequestParam("archivo") MultipartFile archivo,
                        @RequestParam("legajoId") UUID legajoId,
                        @RequestParam("requisitoId") UUID requisitoId,
                        @RequestParam("uploadedBy") String uploadedBy,
                        @RequestHeader("X-LEGGA-TENANT-ID") String tenantId,
                        @RequestHeader("X-LEGGA-OBJECT-ID") String objectId) {

                Documento documento = documentoStorageService.cargarDocumento(
                                archivo,
                                legajoId,
                                requisitoId,
                                uploadedBy,
                                tenantId,
                                objectId);

                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .body(convertirAResponse(documento));
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

                List<DocumentoResponse> documentos = documentoService
                                .buscarPorLegajoId(legajoId)
                                .stream()
                                .map(this::convertirAResponse)
                                .toList();

                return ResponseEntity.ok(documentos);
        }

        /**
         * Consulta los documentos activos asociados a un requisito.
         *
         * GET /api/documentos/requisito/{requisitoId}
         */
        @GetMapping("/requisito/{requisitoId}")
        public ResponseEntity<List<DocumentoResponse>> buscarPorRequisito(
                        @PathVariable UUID requisitoId) {

                List<DocumentoResponse> documentos = documentoService
                                .buscarPorRequisitoId(requisitoId)
                                .stream()
                                .map(this::convertirAResponse)
                                .toList();

                return ResponseEntity.ok(documentos);
        }

        /**
         * Desvincula completamente un documento.
         *
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
         * Genera un enlace temporal de descarga.
         *
         * GET /api/documentos/{id}/descarga
         */
        @GetMapping("/{id}/descarga")
        public ResponseEntity<Map<String, Object>> generarEnlaceDescarga(
                        @PathVariable UUID id) {

                return documentoService.buscarPorId(id)
                                .map(documento -> {

                                        String urlDescarga = azureBlobService
                                                        .generarUrlDescargaSas(documento.getStorageKey());

                                        Map<String, Object> respuesta = Map.of(
                                                        "documentoId", documento.getId(),
                                                        "urlDescarga", urlDescarga,
                                                        "expiraEnSegundos", 300);

                                        return ResponseEntity.ok(respuesta);
                                })
                                .orElse(ResponseEntity.notFound().build());
        }

        /**
         * Actualiza el nombre visible de un documento.
         *
         * PATCH /api/documentos/{id}/nombre
         */
        @PatchMapping("/{id}/nombre")
        public ResponseEntity<DocumentoResponse> actualizarNombreVisible(
                        @PathVariable UUID id,
                        @RequestBody ActualizarNombreDocumentoRequest request) {

                return documentoService.actualizarNombreVisible(
                                id,
                                request.nombreVisible())
                                .map(this::convertirAResponse)
                                .map(ResponseEntity::ok)
                                .orElse(ResponseEntity.notFound().build());
        }

        /**
         * Convierte Documento en DocumentoResponse.
         */
        private DocumentoResponse convertirAResponse(Documento documento) {

                return new DocumentoResponse(
                                documento.getId(),
                                documento.getLegajoId(),
                                documento.getInstitucionId(),
                                documentoService.obtenerRequisitoIds(documento.getId()),
                                documento.getNombreOriginal(),
                                documento.getNombreVisible(),
                                documento.getTipoMime(),
                                documento.getTamanoBytes(),
                                documento.getEstado(),
                                documento.getUploadedBy(),
                                documento.getCreatedAt(),
                                documento.getUpdatedAt());
        }

        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<Map<String, String>> manejarErrorValidacion(
                        IllegalArgumentException ex) {

                return ResponseEntity.badRequest()
                                .body(Map.of("error", ex.getMessage()));
        }

        @ExceptionHandler(IllegalStateException.class)
        public ResponseEntity<Map<String, String>> manejarErrorProceso(
                        IllegalStateException ex) {

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(Map.of("error", ex.getMessage()));
        }

        @ExceptionHandler(LegajoNoEncontradoException.class)
        public ResponseEntity<Map<String, String>> manejarLegajoNoEncontrado(
                        LegajoNoEncontradoException ex) {

                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(Map.of("error", ex.getMessage()));
        }

        @ExceptionHandler(LegajoServiceException.class)
        public ResponseEntity<Map<String, String>> manejarErrorMSLegajos(
                        LegajoServiceException ex) {

                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                .body(Map.of("error", ex.getMessage()));
        }

        @ExceptionHandler(LegajoEstadoInvalidoException.class)
        public ResponseEntity<Map<String, String>> manejarEstadoLegajoInvalido(
                        LegajoEstadoInvalidoException ex) {

                return ResponseEntity.badRequest()
                                .body(Map.of("error", ex.getMessage()));
        }
}
