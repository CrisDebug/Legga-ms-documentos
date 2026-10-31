package cl.legga.documentos.storage.controller;

import cl.legga.documentos.storage.AzureBlobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/storage")
public class StorageHealthController {

    private final AzureBlobService azureBlobService;

    public StorageHealthController(AzureBlobService azureBlobService) {
        this.azureBlobService = azureBlobService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> verificarContenedor() {
        boolean existe = azureBlobService.contenedorExiste();

        return ResponseEntity.ok(Map.of(
                "servicio", "azure-blob-storage",
                "contenedor", "documentos",
                "existe", existe,
                "estado", existe ? "UP" : "DOWN"));
    }
}