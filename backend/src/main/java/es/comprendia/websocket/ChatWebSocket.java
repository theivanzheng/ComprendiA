package es.comprendia.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.comprendia.dto.ConsultaConversacionDTO;
import es.comprendia.servicio.ChatGptServicio;
import es.comprendia.servicio.RagServicio;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * Chat del asistente en tiempo real mediante WebSocket con respuesta en streaming.
 *
 * El cliente abre /ws/chat/{idVideo} y envía un JSON con la pregunta, el historial reciente y la
 * entidad reciente (igual cuerpo que el endpoint REST /conversar). El servidor:
 *   1) recupera los fragmentos relevantes (RAG, en transacción),
 *   2) pide a OpenAI la respuesta en streaming y reenvía cada token según llega
 *      -> mensaje { "tipo": "token", "contenido": "..." }
 *   3) al terminar envía las fuentes (momentos del vídeo)
 *      -> mensaje { "tipo": "fin", "fuentes": [...] }
 *   en caso de fallo -> { "tipo": "error", "mensaje": "..." }
 */
@WebSocket(path = "/ws/chat/{idVideo}")
public class ChatWebSocket {

    private static final Logger LOG = Logger.getLogger(ChatWebSocket.class);

    @Inject RagServicio ragServicio;
    @Inject ChatGptServicio chatGptServicio;
    @Inject ObjectMapper mapeadorJson;

    @OnTextMessage
    @Blocking
    public void alMensaje(String mensaje, WebSocketConnection conexion) {
        Long idVideo;
        ConsultaConversacionDTO consulta;
        try {
            idVideo = Long.valueOf(conexion.pathParam("idVideo"));
            consulta = mapeadorJson.readValue(mensaje, ConsultaConversacionDTO.class);
        } catch (Exception e) {
            enviar(conexion, Map.of("tipo", "error", "mensaje", "Mensaje no válido"));
            return;
        }

        if (consulta.pregunta() == null || consulta.pregunta().isBlank()) {
            enviar(conexion, Map.of("tipo", "error", "mensaje", "La pregunta no puede estar vacía"));
            return;
        }

        try {
            // 1) Recuperación (RAG) en transacción.
            RagServicio.PreparacionRag preparacion = ragServicio.prepararConversacion(idVideo, consulta);

            if (preparacion.fuentes().isEmpty()) {
                enviar(conexion, Map.of("tipo", "token",
                    "contenido", "Todavía no tengo la transcripción procesada para responder sobre este vídeo."));
                enviar(conexion, Map.of("tipo", "fin", "fuentes", preparacion.fuentes()));
                return;
            }

            // 2) Generación en streaming: cada token se reenvía al cliente según llega.
            chatGptServicio.completarConversacionStream(
                preparacion.contexto(),
                consulta.pregunta(),
                consulta.historial(),
                consulta.entidadReciente(),
                token -> enviar(conexion, Map.of("tipo", "token", "contenido", token))
            );

            // 3) Fin: se envían las fuentes (momentos relacionados del vídeo).
            enviar(conexion, Map.of("tipo", "fin", "fuentes", preparacion.fuentes()));

        } catch (Exception e) {
            LOG.warnf("[WS-Chat] Error respondiendo en vídeo %s: %s", idVideo, e.getMessage());
            enviar(conexion, Map.of("tipo", "error",
                "mensaje", "No se pudo generar la respuesta. Inténtalo de nuevo."));
        }
    }

    private void enviar(WebSocketConnection conexion, Map<String, Object> contenido) {
        try {
            if (conexion.isOpen()) {
                conexion.sendTextAndAwait(mapeadorJson.writeValueAsString(contenido));
            }
        } catch (Exception e) {
            LOG.debugf("[WS-Chat] Envío fallido: %s", e.getMessage());
        }
    }
}
