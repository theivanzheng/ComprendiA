package es.comprendia.servicio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.comprendia.dto.MensajeChatDTO;
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
public class ChatGptServicio {

    private static final Logger LOG = Logger.getLogger(ChatGptServicio.class);
    private static final String URL_CHAT = "https://api.openai.com/v1/chat/completions";
    private static final String MODELO = "gpt-4o-mini";
    private static final String SISTEMA =
        "Eres un asistente educativo cercano que ayuda a un alumno a entender una clase grabada. " +
        "Respondes en lenguaje natural, humano y útil, como lo haría un buen profesor. " +
        "Te basas únicamente en los extractos de transcripción que se te proporcionan; " +
        "si la información no aparece, dilo con prudencia y no inventes. " +
        "Reglas de estilo obligatorias: " +
        "1) Nunca menciones 'fragmento', ni números entre corchetes como [1] o [2], ni hables de 'fuentes usadas'. " +
        "2) Cuando cites un momento del vídeo, hazlo en formato minuto:segundo (por ejemplo 0:09 o 1:15), nunca en 'segundos'. " +
        "3) Si la pregunta es '¿en qué momento...?' o '¿cuándo...?', responde directamente con el tiempo en formato m:ss y una frase breve. " +
        "4) Si la pregunta pide una explicación, responde de forma natural y breve, sin tono robótico. " +
        "5) Puedes añadir 'aproximadamente' si el momento no es exacto. " +
        "Responde en el mismo idioma de la pregunta. Sé breve y claro.";

    private static final String SISTEMA_CONVERSACION =
        "Eres un asistente educativo cercano que ayuda a un alumno a entender una clase grabada. " +
        "Respondes en lenguaje natural, humano y útil, como lo haría un buen profesor. " +
        "Te basas únicamente en los extractos de transcripción que se te proporcionan; " +
        "si la información no aparece, dilo con prudencia y no inventes. " +
        "Reglas de estilo obligatorias: " +
        "1) Nunca menciones 'fragmento', ni números entre corchetes como [1] o [2], ni hables de 'fuentes usadas'. " +
        "2) Cuando cites un momento del vídeo, hazlo en formato minuto:segundo (por ejemplo 0:09 o 1:15), nunca en 'segundos'. " +
        "3) Si la pregunta es '¿en qué momento...?' o '¿cuándo...?', responde directamente con el tiempo en formato m:ss y una frase breve. " +
        "4) Si la pregunta pide una explicación, responde de forma natural y breve, sin tono robótico. " +
        "5) Puedes añadir 'aproximadamente' si el momento no es exacto. " +
        "6) El contenido puede estar en otro idioma (por ejemplo inglés); tradúcelo y responde igualmente. " +
        "7) Si el alumno usa un pronombre o una alusión ('el móvil', 'ese', 'y después?', 'cuánto costaba?'), " +
        "interpreta a qué se refería por los mensajes anteriores. " +
        "Responde en el mismo idioma de la pregunta. Sé breve y claro.";

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

    /**
     * Respuesta conversacional: incluye el historial reciente (memoria corta del frontend)
     * y una pista de entidad reciente para resolver referencias implícitas.
     */
    public String completarConversacion(String contexto, String pregunta,
                                        List<MensajeChatDTO> historial, String entidadReciente) {
        if (claveApi.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY no configurada");
        }

        String cuerpo = construirCuerpoConversacion(contexto, pregunta, historial, entidadReciente, false);
        return enviarSolicitud(cuerpo);
    }

    /**
     * Igual que completarConversacion, pero en modo streaming: pide a OpenAI la respuesta token
     * a token (stream=true) y entrega cada fragmento al consumidor 'onChunk' según va llegando.
     * Devuelve además el texto completo acumulado.
     */
    public String completarConversacionStream(String contexto, String pregunta,
                                              List<MensajeChatDTO> historial, String entidadReciente,
                                              java.util.function.Consumer<String> onChunk) {
        if (claveApi.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY no configurada");
        }
        // Diagnóstico: qué recibe realmente la LLM (tamaño de contexto, nº de turnos, pregunta).
        int turnos = historial == null ? 0 : historial.size();
        int largoContexto = contexto == null ? 0 : contexto.length();
        LOG.infof("[GPT-stream] contexto=%d chars, historial=%d turnos, entidad='%s', pregunta='%s'",
            largoContexto, turnos, entidadReciente, pregunta);
        String cuerpo = construirCuerpoConversacion(contexto, pregunta, historial, entidadReciente, true);
        return enviarSolicitudStream(cuerpo, onChunk);
    }

    public String completarPersonalizado(String sistema, String usuario, int maxTokens, double temperature) {
        if (claveApi.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY no configurada");
        }

        String cuerpo = construirCuerpoPersonalizado(sistema, usuario, maxTokens, temperature);
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

    /**
     * Envía la petición a OpenAI en modo streaming. La respuesta llega como Server-Sent Events:
     * líneas "data: {json}" donde cada trozo trae choices[0].delta.content. Por cada token se
     * invoca onChunk. La secuencia termina con "data: [DONE]". Devuelve el texto completo.
     */
    private String enviarSolicitudStream(String cuerpo, java.util.function.Consumer<String> onChunk) {
        HttpRequest solicitud = HttpRequest.newBuilder()
            .uri(URI.create(URL_CHAT))
            .header("Authorization", "Bearer " + claveApi)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(90))
            .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
            .build();

        StringBuilder completo = new StringBuilder();
        try {
            long inicio = System.currentTimeMillis();
            LOG.info("[GPT-stream] Iniciando streaming a OpenAI");
            HttpResponse<java.util.stream.Stream<String>> respuesta =
                clienteHttp.send(solicitud, HttpResponse.BodyHandlers.ofLines());

            if (respuesta.statusCode() != 200) {
                String error = respuesta.body().reduce("", (a, b) -> a + b);
                // Se neutralizan las llaves del cuerpo de error para que el formateador de logs
                // no las interprete como argumentos ({...}).
                LOG.errorf("[GPT-stream] OpenAI respondió HTTP %d: %s",
                    respuesta.statusCode(), error.replace('{', '(').replace('}', ')'));
                throw new IllegalStateException("Error de OpenAI (HTTP " + respuesta.statusCode() + "): " + error);
            }

            respuesta.body().forEach(linea -> {
                if (linea == null || !linea.startsWith("data:")) return;
                String dato = linea.substring("data:".length()).trim();
                if (dato.isEmpty() || dato.equals("[DONE]")) return;
                try {
                    JsonNode nodo = mapeadorJson.readTree(dato);
                    JsonNode contenido = nodo.path("choices").path(0).path("delta").path("content");
                    if (contenido.isTextual()) {
                        String token = contenido.asText();
                        completo.append(token);
                        onChunk.accept(token);
                    }
                } catch (Exception ignorado) {
                    // Trozo no interpretable: se ignora y se sigue con el resto del stream.
                }
            });
            LOG.infof("[GPT-stream] Completado en %d ms (%d caracteres)",
                System.currentTimeMillis() - inicio, completo.length());
            return completo.toString();

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            LOG.errorf(e, "[GPT-stream] Error de red llamando a OpenAI: %s", e.toString());
            throw new IllegalStateException("Error al llamar a GPT (stream): " + e.getMessage(), e);
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

    private String construirCuerpoConversacion(String contexto, String pregunta,
                                              List<MensajeChatDTO> historial, String entidadReciente,
                                              boolean stream) {
        try {
            List<Map<String, String>> mensajes = new ArrayList<>();
            mensajes.add(Map.of("role", "system", "content", SISTEMA_CONVERSACION));

            // Historial reciente (memoria corta). Se cortan tamaños desorbitados por seguridad.
            if (historial != null) {
                for (MensajeChatDTO turno : historial) {
                    if (turno == null || turno.contenido() == null || turno.contenido().isBlank()) continue;
                    String role = "assistant".equalsIgnoreCase(turno.rol()) ? "assistant" : "user";
                    String contenido = turno.contenido().length() > 1200
                        ? turno.contenido().substring(0, 1200) : turno.contenido();
                    mensajes.add(Map.of("role", role, "content", contenido));
                }
            }

            // Mismo formato que el endpoint /responder (que funciona bien): contexto + "Pregunta:".
            StringBuilder usuario = new StringBuilder();
            usuario.append("Fragmentos relevantes de la transcripcion:\n").append(contexto);
            if (entidadReciente != null && !entidadReciente.isBlank()) {
                usuario.append("\n(Si la pregunta usa un pronombre o alusión, probablemente se refiere a: ")
                       .append(entidadReciente).append(")");
            }
            usuario.append("\n\nPregunta: ").append(pregunta);

            // ¡IMPRESCINDIBLE! Añadir el mensaje del usuario (contexto + pregunta actual) a la lista.
            // Sin esta línea, a la LLM solo le llegaban el system y el historial, no la pregunta actual.
            mensajes.add(Map.of("role", "user", "content", usuario.toString()));

            Map<String, Object> cuerpo = new java.util.HashMap<>();
            cuerpo.put("model", MODELO);
            cuerpo.put("temperature", 0.3);
            cuerpo.put("max_tokens", 500);
            cuerpo.put("messages", mensajes);
            if (stream) {
                cuerpo.put("stream", true); // OpenAI devolverá la respuesta token a token (SSE)
            }
            return mapeadorJson.writeValueAsString(cuerpo);
        } catch (Exception e) {
            throw new IllegalStateException("Error al construir el cuerpo conversacional", e);
        }
    }

    private String construirCuerpoPersonalizado(String sistema, String usuario, int maxTokens, double temperature) {
        try {
            return mapeadorJson.writeValueAsString(Map.of(
                "model", MODELO,
                "temperature", temperature,
                "max_tokens", maxTokens,
                "messages", List.of(
                    Map.of("role", "system", "content", sistema),
                    Map.of("role", "user", "content", usuario)
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
