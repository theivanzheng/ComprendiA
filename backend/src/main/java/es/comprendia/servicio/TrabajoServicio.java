package es.comprendia.servicio;

import es.comprendia.dto.EstadoTrabajoDTO;
import es.comprendia.dto.EstadoTrabajoDTO.Fase;
import es.comprendia.dto.RespuestaTranscripcionDTO;
import es.comprendia.websocket.RegistroTrabajosWebSocket;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class TrabajoServicio {

    private final ConcurrentHashMap<String, EstadoTrabajoDTO> trabajos = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Thread> hilos = new ConcurrentHashMap<>();

    @Inject
    RegistroTrabajosWebSocket registroWebSocket;

    public String crearTrabajo() {
        String id = UUID.randomUUID().toString();
        trabajos.put(id, new EstadoTrabajoDTO(id, Fase.DESCARGANDO));
        return id;
    }

    public void actualizarFase(String id, Fase fase) {
        EstadoTrabajoDTO estado = trabajos.get(id);
        if (estado != null && estado.getFase() != Fase.CANCELADO) {
            estado.setFase(fase);
            registroWebSocket.publicar(id, estado);
        }
    }

    public void completar(String id, RespuestaTranscripcionDTO resultado) {
        EstadoTrabajoDTO estado = trabajos.get(id);
        if (estado != null && estado.getFase() != Fase.CANCELADO) {
            estado.setResultado(resultado);
            estado.setFase(Fase.COMPLETADO);
            registroWebSocket.publicar(id, estado);
        }
        hilos.remove(id);
    }

    public void marcarError(String id, String mensaje) {
        EstadoTrabajoDTO estado = trabajos.get(id);
        if (estado != null && estado.getFase() != Fase.CANCELADO) {
            estado.setError(mensaje);
            estado.setFase(Fase.ERROR);
            registroWebSocket.publicar(id, estado);
        }
        hilos.remove(id);
    }

    public void registrarHilo(String id, Thread hilo) {
        hilos.put(id, hilo);
    }

    public boolean cancelar(String id) {
        EstadoTrabajoDTO estado = trabajos.get(id);
        if (estado == null) return false;
        if (estado.getFase() == Fase.COMPLETADO || estado.getFase() == Fase.ERROR) return true;

        estado.setFase(Fase.CANCELADO);
        estado.setError("Trabajo cancelado por el usuario");
        registroWebSocket.publicar(id, estado);

        Thread hilo = hilos.remove(id);
        if (hilo != null) {
            hilo.interrupt();
        }
        return true;
    }

    public boolean estaCancelado(String id) {
        EstadoTrabajoDTO estado = trabajos.get(id);
        return estado != null && estado.getFase() == Fase.CANCELADO;
    }

    public void finalizarHilo(String id) {
        hilos.remove(id);
    }

    public Optional<EstadoTrabajoDTO> obtener(String id) {
        return Optional.ofNullable(trabajos.get(id));
    }
}
