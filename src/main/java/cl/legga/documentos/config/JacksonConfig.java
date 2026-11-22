package cl.legga.documentos.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de Jackson para ms-documentos.
 */
@Configuration
public class JacksonConfig {

    /**
     * Registra ObjectMapper como un bean de Spring.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}