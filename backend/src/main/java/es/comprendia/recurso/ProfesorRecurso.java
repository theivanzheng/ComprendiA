package es.comprendia.recurso;

import es.comprendia.dto.ProfesorDTO;
import es.comprendia.dto.SolicitudProfesorDTO;
import es.comprendia.servicio.ProfesorServicio;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Blocking
@Path("/api/profesores")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProfesorRecurso {

    @Inject
    ProfesorServicio profesorServicio;

    @GET
    public List<ProfesorDTO> obtenerTodos() {
        return profesorServicio.obtenerTodos();
    }

    @POST
    @Transactional
    public Response crear(SolicitudProfesorDTO solicitud) {
        try {
            ProfesorDTO dto = profesorServicio.crear(solicitud);
            return Response.status(Response.Status.CREATED).entity(dto).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", e.getMessage())).build();
        }
    }
}
