package es.comprendia.recurso;

import es.comprendia.dto.ModeloChatDTO;
import es.comprendia.servicio.ChatGptServicio;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * Expone los modelos de chat disponibles para que el frontend pinte el selector (multi-modelo).
 */
@Path("/api/modelos")
public class ModeloRecurso {

    @Inject
    ChatGptServicio chatGptServicio;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<ModeloChatDTO> listar() {
        return chatGptServicio.modelosDisponibles();
    }
}
