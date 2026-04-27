package es.comprendia.servicio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.comprendia.excepcion.ExcepcionEmbedding;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class EmbeddingServicio {

    private static final Logger LOG = Logger.getLogger(EmbeddingServicio.class);
    private static final String URL_EMBEDDINGS = "https://api.openai.com/v1/embeddings";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @ConfigProperty(name = "comprendia.openai.api.clave", defaultValue = "")
    String claveApi;

    @PostConstruct
    void verificarConfiguracion() {
        if (claveApi.isBlank()) {
            LOG.warn("[Embedding] ATENCIÓN: comprendia.openai.api.clave está vacía. " +
                "Verifica que OPENAI_API_KEY esté exportada en el entorno del servidor.");
        } else {
            LOG.infof("[Embedding] EmbeddingServicio listo. Clave configurada (longitud=%d chars).", claveApi.length());
        }
    }

    private final HttpClient clienteHttp = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private final ObjectMapper mapeadorJson = new ObjectMapper();

    public List<Double> generarEmbedding(String texto) {
        if (claveApi.isBlank()) {
            throw new ExcepcionEmbedding("Clave de API de OpenAI no configurada (OPENAI_API_KEY vacía)");
        }
        HttpRequest solicitud = HttpRequest.newBuilder()
            .uri(URI.create(URL_EMBEDDINGS))
            .header("Authorization", "Bearer " + claveApi)
            .header("Content-Type", "application/json")
            .timeout(TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofString(construirCuerpoJson(texto)))
            .build();
        try {
            HttpResponse<String> respuesta = clienteHttp.send(solicitud, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() != 200) {
                throw new ExcepcionEmbedding(
                    "OpenAI respondió con código " + respuesta.statusCode() + " — body: " + respuesta.body());
            }
            return parsearEmbedding(respuesta.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new ExcepcionEmbedding("Error de red al llamar a OpenAI Embeddings: " + e.getMessage(), e);
        }
    }

    private String construirCuerpoJson(String texto) {
        try {
            return mapeadorJson.writeValueAsString(Map.of(
                "model", "text-embedding-3-small",
                "input", texto
            ));
        } catch (Exception e) {
            throw new ExcepcionEmbedding("Error al serializar petición de embedding", e);
        }
    }

    private List<Double> parsearEmbedding(String json) {
        try {
            JsonNode nodoEmbedding = mapeadorJson.readTree(json)
                .path("data").path(0).path("embedding");
            if (!nodoEmbedding.isArray()) {
                throw new ExcepcionEmbedding("Respuesta de OpenAI sin array de embedding");
            }
            List<Double> embedding = new ArrayList<>();
            for (JsonNode valor : nodoEmbedding) {
                embedding.add(valor.asDouble());
            }
            return embedding;
        } catch (ExcepcionEmbedding e) {
            throw e;
        } catch (Exception e) {
            throw new ExcepcionEmbedding("Error al parsear respuesta de OpenAI", e);
        }
    }
}
