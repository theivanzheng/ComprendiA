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
        "Eres el copiloto conversacional de un vídeo de clase. Acompañas al alumno en una conversación " +
        "continua sobre el contenido del vídeo, como lo haría un buen profesor cercano. " +
        "Tienes acceso al historial reciente de la conversación y a extractos relevantes de la transcripción. " +
        "Comportamiento obligatorio: " +
        "1) Mantén el hilo: resuelve referencias implícitas usando el contexto previo. Si el alumno dice " +
        "'el móvil', 'ese reloj', 'y después?', 'cuánto costaba?', entiende a qué se refería en mensajes anteriores. " +
        "2) Responde de forma natural, breve y útil, en 1-3 frases. Nada de respuestas enormes. " +
        "3) Te basas SOLO en los extractos proporcionados; si la información no aparece, dilo con prudencia y no inventes. " +
        "4) Cuando cites un momento, usa formato minuto:segundo (por ejemplo 2:14), nunca 'segundos'. " +
        "5) Nunca menciones 'fragmento', ni números entre corchetes como [1], ni 'fuentes usadas'. " +
        "6) Prohibido Markdown pesado: nada de encabezados con #, ni listas largas, ni negritas. Tono humano, no robótico. " +
        "7) Responde SIEMPRE de forma directa a lo que se pregunta. Si el alumno pregunta si el vídeo " +
        "habla de algo concreto (por ejemplo '¿habla de X?' o '¿menciona X?'), contesta con claridad: si X " +
        "aparece en los extractos, di que sí, en qué minuto y qué dice; si NO aparece en los extractos, di con " +
        "naturalidad que ahí no se menciona. " +
        "8) Nunca respondas con evasivas como '¿sobre qué parte te gustaría hablar?'. Da siempre una respuesta " +
        "útil con la información que tengas en los extractos. " +
        "Ejemplo de buen estilo: 'Habla del iPhone 17 Pro Max sobre el minuto 2:14. Comenta sobre todo el nuevo diseño y la cámara.' " +
        "Responde en el mismo idioma de la pregunta.";

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
                LOG.errorf("[GPT-stream] OpenAI respondió HTTP %d: %s", respuesta.statusCode(), error);
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

            StringBuilder usuario = new StringBuilder();
            usuario.append("Extractos relevantes de la transcripcion del video:\n").append(contexto);
            if (entidadReciente != null && !entidadReciente.isBlank()) {
                usuario.append("\nContexto reciente de la conversacion (posible referente de pronombres o alusiones): ")
                       .append(entidadReciente).append(".");
            }
            usuario.append("\n\nMensaje del alumno: ").append(pregunta);

            Map<String, Object> cuerpo = new java.util.HashMap<>();
            cuerpo.put("model", MODELO);
            cuerpo.put("temperature", 0.3);
            cuerpo.put("max_tokens", 320);
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
