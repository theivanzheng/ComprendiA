package es.comprendia.recurso;

import es.comprendia.dto.SolicitudCapituloDTO;
import es.comprendia.servicio.CapituloConceptoServicio;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Blocking
@Path("/api/capitulos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CapituloRecurso {

    @Inject
    CapituloConceptoServicio capituloConceptoServicio;

    @PATCH
    @Path("/{id}")
    @Transactional
    public Response actualizar(@PathParam("id") Long id, SolicitudCapituloDTO solicitud) {
        try {
            return Response.ok(capituloConceptoServicio.actualizarCapitulo(id, solicitud)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response eliminar(@PathParam("id") Long id) {
        try {
            capituloConceptoServicio.eliminarCapitulo(id);
            return Response.noContent().build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
        }
    }
}
