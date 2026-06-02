package es.comprendia.servicio;

import es.comprendia.dto.EstadoTrabajoDTO;
import es.comprendia.dto.EstadoTrabajoDTO.Fase;
import es.comprendia.dto.RespuestaTranscripcionDTO;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class TrabajoServicio {

    private final ConcurrentHashMap<String, EstadoTrabajoDTO> trabajos = new ConcurrentHashMap<>();

    public String crearTrabajo() {
        String id = UUID.randomUUID().toString();
        trabajos.put(id, new EstadoTrabajoDTO(id, Fase.DESCARGANDO));
        return id;
    }

    public void actualizarFase(String id, Fase fase) {
        EstadoTrabajoDTO estado = trabajos.get(id);
        if (estado != null) estado.setFase(fase);
    }

    public void completar(String id, RespuestaTranscripcionDTO resultado) {
        EstadoTrabajoDTO estado = trabajos.get(id);
        if (estado != null) {
            estado.setResultado(resultado);
            estado.setFase(Fase.COMPLETADO);
        }
    }

    public void marcarError(String id, String mensaje) {
        EstadoTrabajoDTO estado = trabajos.get(id);
        if (estado != null) {
            estado.setError(mensaje);
            estado.setFase(Fase.ERROR);
        }
    }

    public Optional<EstadoTrabajoDTO> obtener(String id) {
        return Optional.ofNullable(trabajos.get(id));
    }
}
