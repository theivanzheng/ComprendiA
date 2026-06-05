package es.comprendia.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.comprendia.servicio.TrabajoServicio;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Canal WebSocket por el que el cliente recibe en tiempo real el progreso del análisis de un
 * vídeo. El cliente se conecta a /ws/trabajos/{idTrabajo} y recibe el estado actual al abrir y
 * cada cambio de fase hasta COMPLETADO, CANCELADO o ERROR.
 */
@WebSocket(path = "/ws/trabajos/{idTrabajo}")
public class TrabajoWebSocket {

    private static final Logger LOG = Logger.getLogger(TrabajoWebSocket.class);

    @Inject
    RegistroTrabajosWebSocket registro;

    @Inject
    TrabajoServicio trabajoServicio;

    @Inject
    ObjectMapper mapeadorJson;

    @OnOpen
    public void alAbrir(WebSocketConnection conexion) {
        String idTrabajo = conexion.pathParam("idTrabajo");
        registro.registrar(idTrabajo, conexion);
        LOG.infof("[WS] Cliente conectado al trabajo %s", idTrabajo);

        // Enviar de inmediato el estado actual (por si el trabajo ya avanzó antes de conectar).
        trabajoServicio.obtener(idTrabajo).ifPresent(estado -> {
            try {
                conexion.sendTextAndAwait(mapeadorJson.writeValueAsString(estado));
            } catch (Exception e) {
                LOG.debugf("[WS] No se pudo enviar el estado inicial del trabajo %s: %s",
                    idTrabajo, e.getMessage());
            }
        });
    }

    @OnClose
    public void alCerrar(WebSocketConnection conexion) {
        String idTrabajo = conexion.pathParam("idTrabajo");
        registro.eliminar(idTrabajo, conexion);
        LOG.infof("[WS] Cliente desconectado del trabajo %s", idTrabajo);
    }
}
