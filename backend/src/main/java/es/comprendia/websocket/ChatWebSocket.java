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
            LOG.errorf(e, "[WS-Chat] Mensaje no válido (pathParam idVideo='%s', mensaje='%s')",
                conexion.pathParam("idVideo"), recortar(mensaje));
            enviar(conexion, Map.of("tipo", "error", "mensaje", "Mensaje no válido"));
            return;
        }

        if (consulta.pregunta() == null || consulta.pregunta().isBlank()) {
            LOG.warnf("[WS-Chat] Pregunta vacía (videoId=%s)", idVideo);
            enviar(conexion, Map.of("tipo", "error", "mensaje", "La pregunta no puede estar vacía"));
            return;
        }

        int numHistorial = consulta.historial() == null ? 0 : consulta.historial().size();
        LOG.infof("[WS-Chat] Pregunta recibida (videoId=%s, turnos previos=%d, entidad='%s'): %s",
            idVideo, numHistorial, consulta.entidadReciente(), recortar(consulta.pregunta()));

        try {
            // 1) Recuperación (RAG) en transacción.
            long inicio = System.currentTimeMillis();
            RagServicio.PreparacionRag preparacion = ragServicio.prepararConversacion(idVideo, consulta);
            LOG.infof("[WS-Chat] Recuperados %d fragmentos en %d ms (videoId=%s)",
                preparacion.fuentes().size(), System.currentTimeMillis() - inicio, idVideo);

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
                preparacion.entidadEfectiva(),   // null salvo que la pregunta sea referencia implícita
                token -> enviar(conexion, Map.of("tipo", "token", "contenido", token))
            );

            // 3) Fin: se envían las fuentes (momentos relacionados del vídeo).
            enviar(conexion, Map.of("tipo", "fin", "fuentes", preparacion.fuentes()));
            LOG.infof("[WS-Chat] Respuesta completada (videoId=%s)", idVideo);

        } catch (Exception e) {
            // Se registra el stack trace completo (incluye el detalle del error). No se mete
            // e.toString() en el formato porque puede traer llaves { } del cuerpo de OpenAI y
            // el formateador de logs las interpretaría como argumentos.
            LOG.errorf(e, "[WS-Chat] FALLO al responder (videoId=%s, pregunta='%s')",
                idVideo, recortar(consulta.pregunta()));
            enviar(conexion, Map.of("tipo", "error",
                "mensaje", "No se pudo generar la respuesta. Inténtalo de nuevo."));
        }
    }

    // Recorta textos largos para que los logs sean legibles.
    private String recortar(String texto) {
        if (texto == null) return "null";
        String limpio = texto.replaceAll("\\s+", " ").strip();
        return limpio.length() > 200 ? limpio.substring(0, 200) + "…" : limpio;
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
