package es.comprendia.servicio;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import es.comprendia.dto.MensajeChatDTO;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Capa de interacción con el modelo de lenguaje (chat) usando LangChain4j.
 *
 * En vez de montar la petición HTTP a OpenAI a mano, se usan los modelos de LangChain4j
 * ({@link ChatLanguageModel} y {@link StreamingChatLanguageModel}). Como cada método necesita
 * distintos parámetros (temperatura, max tokens, modo JSON), los modelos se construyen y cachean
 * por combinación de parámetros. Las firmas públicas no cambian: el resto del sistema (RAG,
 * análisis, clasificación, chat por WebSocket) sigue igual.
 */
@ApplicationScoped
public class ChatGptServicio {

    private static final Logger LOG = Logger.getLogger(ChatGptServicio.class);
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

    // Modelos de LangChain4j cacheados por combinación de parámetros (maxTokens|temp|json).
    private final Map<String, ChatLanguageModel> cacheChat = new ConcurrentHashMap<>();
    private final Map<String, StreamingChatLanguageModel> cacheStream = new ConcurrentHashMap<>();

    private ChatLanguageModel chat(int maxTokens, double temperature, boolean json) {
        return cacheChat.computeIfAbsent(maxTokens + "|" + temperature + "|" + json, clave -> {
            OpenAiChatModel.OpenAiChatModelBuilder constructor = OpenAiChatModel.builder()
                .apiKey(claveApi)
                .modelName(MODELO)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(90));
            if (json) {
                constructor.responseFormat("json_object"); // respuesta JSON garantizada
            }
            return constructor.build();
        });
    }

    private StreamingChatLanguageModel chatStream(int maxTokens, double temperature) {
        return cacheStream.computeIfAbsent(maxTokens + "|" + temperature, clave ->
            OpenAiStreamingChatModel.builder()
                .apiKey(claveApi)
                .modelName(MODELO)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(120))
                .build());
    }

    public String completar(String contexto, String pregunta) {
        List<ChatMessage> mensajes = List.of(
            SystemMessage.from(SISTEMA),
            UserMessage.from("Fragmentos relevantes de la transcripcion:\n" + contexto + "\n\nPregunta: " + pregunta));
        return generar(chat(600, 0.3, false), mensajes);
    }

    /**
     * Respuesta conversacional: incluye el historial reciente (memoria corta del frontend)
     * y una pista de entidad reciente para resolver referencias implícitas.
     */
    public String completarConversacion(String contexto, String pregunta,
                                        List<MensajeChatDTO> historial, String entidadReciente) {
        return generar(chat(500, 0.3, false),
            mensajesConversacion(contexto, pregunta, historial, entidadReciente));
    }

    /**
     * Igual que completarConversacion, pero en streaming: cada token llega por 'onChunk' según
     * lo va generando el modelo. Devuelve el texto completo cuando termina.
     */
    public String completarConversacionStream(String contexto, String pregunta,
                                              List<MensajeChatDTO> historial, String entidadReciente,
                                              Consumer<String> onChunk) {
        int turnos = historial == null ? 0 : historial.size();
        int largoContexto = contexto == null ? 0 : contexto.length();
        LOG.infof("[GPT-stream] contexto=%d chars, historial=%d turnos, entidad='%s', pregunta='%s'",
            largoContexto, turnos, entidadReciente, pregunta);

        List<ChatMessage> mensajes = mensajesConversacion(contexto, pregunta, historial, entidadReciente);
        CompletableFuture<String> futuro = new CompletableFuture<>();
        StringBuilder completo = new StringBuilder();
        long inicio = System.currentTimeMillis();

        chatStream(500, 0.3).generate(mensajes, new StreamingResponseHandler<AiMessage>() {
            @Override public void onNext(String token) {
                completo.append(token);
                onChunk.accept(token);
            }
            @Override public void onComplete(Response<AiMessage> respuesta) {
                futuro.complete(completo.toString());
            }
            @Override public void onError(Throwable error) {
                futuro.completeExceptionally(error);
            }
        });

        try {
            String texto = futuro.get(120, TimeUnit.SECONDS);
            LOG.infof("[GPT-stream] Completado en %d ms (%d caracteres)",
                System.currentTimeMillis() - inicio, texto.length());
            return texto;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("Error al llamar a GPT (stream): " + e.getMessage(), e);
        }
    }

    public String completarPersonalizado(String sistema, String usuario, int maxTokens, double temperature) {
        return generar(chat(maxTokens, temperature, false),
            List.of(SystemMessage.from(sistema), UserMessage.from(usuario)));
    }

    public String completarEstructurado(String sistema, String usuario, int maxTokens) {
        return generar(chat(maxTokens, 0.2, true),
            List.of(SystemMessage.from(sistema), UserMessage.from(usuario)));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String generar(ChatLanguageModel modelo, List<ChatMessage> mensajes) {
        long inicio = System.currentTimeMillis();
        Response<AiMessage> respuesta = modelo.generate(mensajes);
        LOG.infof("[GPT] Completado en %d ms", System.currentTimeMillis() - inicio);
        return respuesta.content().text();
    }

    /** Construye la lista de mensajes del chat conversacional (system + historial + pregunta). */
    private List<ChatMessage> mensajesConversacion(String contexto, String pregunta,
                                                   List<MensajeChatDTO> historial, String entidadReciente) {
        List<ChatMessage> mensajes = new ArrayList<>();
        mensajes.add(SystemMessage.from(SISTEMA_CONVERSACION));

        // Historial reciente (memoria corta). Se cortan tamaños desorbitados por seguridad.
        if (historial != null) {
            for (MensajeChatDTO turno : historial) {
                if (turno == null || turno.contenido() == null || turno.contenido().isBlank()) continue;
                String contenido = turno.contenido().length() > 1200
                    ? turno.contenido().substring(0, 1200) : turno.contenido();
                if ("assistant".equalsIgnoreCase(turno.rol())) {
                    mensajes.add(AiMessage.from(contenido));
                } else {
                    mensajes.add(UserMessage.from(contenido));
                }
            }
        }

        // Mensaje del usuario: extractos del RAG + la pregunta actual.
        StringBuilder usuario = new StringBuilder();
        usuario.append("Fragmentos relevantes de la transcripcion:\n").append(contexto);
        if (entidadReciente != null && !entidadReciente.isBlank()) {
            usuario.append("\n(Si la pregunta usa un pronombre o alusión, probablemente se refiere a: ")
                   .append(entidadReciente).append(")");
        }
        usuario.append("\n\nPregunta: ").append(pregunta);
        mensajes.add(UserMessage.from(usuario.toString()));
        return mensajes;
    }
}
