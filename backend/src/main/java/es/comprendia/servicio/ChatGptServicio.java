package es.comprendia.servicio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ChatGptServicio {

    private static final Logger LOG = Logger.getLogger(ChatGptServicio.class);
    private static final String URL_CHAT = "https://api.openai.com/v1/chat/completions";
    private static final String MODELO = "gpt-4o-mini";
    private static final String SISTEMA =
        "Eres un asistente educativo especializado en analizar clases grabadas. " +
        "Responde únicamente basándote en los fragmentos de transcripción que se te proporcionan. " +
        "Si la respuesta no está en los fragmentos, indícalo claramente. " +
        "Responde en el mismo idioma de la pregunta. Sé conciso y directo.";

    @ConfigProperty(name = "comprendia.openai.api.clave", defaultValue = "")
    String claveApi;

    private final HttpClient clienteHttp = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();
    private final ObjectMapper mapeadorJson = new ObjectMapper();

    public String completar(String contexto, String pregunta) {
        if (claveApi.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY no configurada");
        }

        String cuerpo = construirCuerpo(contexto, pregunta);
        return enviarSolicitud(cuerpo);
    }

    public String completarEstructurado(String sistema, String usuario, int maxTokens) {
        if (claveApi.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY no configurada");
        }

        String cuerpo = construirCuerpoEstructurado(sistema, usuario, maxTokens);
        return enviarSolicitud(cuerpo);
    }

    private String enviarSolicitud(String cuerpo) {
        HttpRequest solicitud = HttpRequest.newBuilder()
            .uri(URI.create(URL_CHAT))
            .header("Authorization", "Bearer " + claveApi)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
            .build();

        try {
            long inicio = System.currentTimeMillis();
            HttpResponse<String> respuesta = clienteHttp.send(solicitud, HttpResponse.BodyHandlers.ofString());
            LOG.infof("[GPT] Completado en %d ms (HTTP %d)", System.currentTimeMillis() - inicio, respuesta.statusCode());

            if (respuesta.statusCode() != 200) {
                throw new IllegalStateException("Error de OpenAI (HTTP " + respuesta.statusCode() + "): " + respuesta.body());
            }

            return parsearRespuesta(respuesta.body());

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("Error al llamar a GPT: " + e.getMessage(), e);
        }
    }

    private String construirCuerpo(String contexto, String pregunta) {
        try {
            return mapeadorJson.writeValueAsString(Map.of(
                "model", MODELO,
                "temperature", 0.3,
                "max_tokens", 600,
                "messages", List.of(
                    Map.of("role", "system", "content", SISTEMA),
                    Map.of("role", "user", "content",
                        "Fragmentos relevantes de la transcripcion:\n" + contexto +
                        "\n\nPregunta: " + pregunta)
                )
            ));
        } catch (Exception e) {
            throw new IllegalStateException("Error al construir el cuerpo de la peticion", e);
        }
    }

    private String construirCuerpoEstructurado(String sistema, String usuario, int maxTokens) {
        try {
            return mapeadorJson.writeValueAsString(Map.of(
                "model", MODELO,
                "temperature", 0.2,
                "max_tokens", maxTokens,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                    Map.of("role", "system", "content", sistema),
                    Map.of("role", "user", "content", usuario)
                )
            ));
        } catch (Exception e) {
            throw new IllegalStateException("Error al construir el cuerpo de la peticion", e);
        }
    }

    private String parsearRespuesta(String json) {
        try {
            JsonNode raiz = mapeadorJson.readTree(json);
            return raiz.path("choices").path(0).path("message").path("content").asText();
        } catch (Exception e) {
            throw new IllegalStateException("Error al parsear respuesta de GPT: " + e.getMessage(), e);
        }
    }
}
