package es.comprendia.recurso;

import es.comprendia.servicio.SaludServicio;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/salud")
public class SaludRecurso {

    @Inject
    SaludServicio saludServicio;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public RespuestaSalud verificarSalud() {
        return new RespuestaSalud("activo", saludServicio.obtenerMensajeSalud());
    }

    public record RespuestaSalud(String estado, String mensaje) {}
}
