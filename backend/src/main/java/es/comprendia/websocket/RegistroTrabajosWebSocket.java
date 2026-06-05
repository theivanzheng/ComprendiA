package es.comprendia.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.comprendia.dto.EstadoTrabajoDTO;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mantiene las conexiones WebSocket abiertas agrupadas por id de trabajo y publica en ellas
 * los cambios de estado del procesamiento (fase, resultado, error) en tiempo real.
 *
 * Sustituye al sondeo (polling) HTTP: el endpoint de estado REST se conserva como alternativa.
 */
@ApplicationScoped
public class RegistroTrabajosWebSocket {

    private static final Logger LOG = Logger.getLogger(RegistroTrabajosWebSocket.class);

    // idTrabajo -> conexiones suscritas a ese trabajo
    private final Map<String, Set<WebSocketConnection>> conexiones = new ConcurrentHashMap<>();

    @Inject
    ObjectMapper mapeadorJson;

    public void registrar(String idTrabajo, WebSocketConnection conexion) {
        conexiones.computeIfAbsent(idTrabajo, clave -> ConcurrentHashMap.newKeySet()).add(conexion);
        LOG.debugf("[WS] Conexión registrada para trabajo %s", idTrabajo);
    }

    public void eliminar(String idTrabajo, WebSocketConnection conexion) {
        Set<WebSocketConnection> grupo = conexiones.get(idTrabajo);
        if (grupo != null) {
            grupo.remove(conexion);
            if (grupo.isEmpty()) {
                conexiones.remove(idTrabajo);
            }
        }
    }

    /** Envía el estado actual del trabajo a todas las conexiones suscritas. */
    public void publicar(String idTrabajo, EstadoTrabajoDTO estado) {
        Set<WebSocketConnection> grupo = conexiones.get(idTrabajo);
        if (grupo == null || grupo.isEmpty()) {
            return;
        }
        String json;
        try {
            json = mapeadorJson.writeValueAsString(estado);
        } catch (Exception e) {
            LOG.warnf("[WS] No se pudo serializar el estado del trabajo %s: %s", idTrabajo, e.getMessage());
            return;
        }
        for (WebSocketConnection conexion : grupo) {
            try {
                if (conexion.isOpen()) {
                    conexion.sendTextAndAwait(json);
                }
            } catch (Exception e) {
                // Conexión caída o en cierre: se ignora; se limpiará en @OnClose.
                LOG.debugf("[WS] Envío fallido en trabajo %s: %s", idTrabajo, e.getMessage());
            }
        }
    }
}
