package es.comprendia.recurso;

import es.comprendia.dto.FragmentoDTO;
import es.comprendia.dto.ResultadoBusquedaDTO;
import es.comprendia.dto.VideoResumenDTO;
import es.comprendia.servicio.BusquedaSemanticaServicio;
import es.comprendia.servicio.FragmentoConsultaServicio;
import es.comprendia.servicio.VideoConsultaServicio;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Blocking
@Path("/api/transcripciones")
@Produces(MediaType.APPLICATION_JSON)
public class TranscripcionConsultaRecurso {

    @Inject
    VideoConsultaServicio videoConsultaServicio;

    @Inject
    FragmentoConsultaServicio fragmentoConsultaServicio;

    @Inject
    BusquedaSemanticaServicio busquedaSemanticaServicio;

    @GET
    public List<VideoResumenDTO> obtenerTranscripciones(
        @QueryParam("page") @DefaultValue("0") int pagina,
        @QueryParam("size") @DefaultValue("10") int tamanyo) {
        return videoConsultaServicio.obtenerVideos(pagina, tamanyo);
    }

    @GET
    @Path("/{id}/fragmentos")
    public List<FragmentoDTO> obtenerFragmentos(@PathParam("id") Long id) {
        return fragmentoConsultaServicio.obtenerPorVideoId(id);
    }

    @GET
    @Path("/{id}/buscar")
    public Response buscarFragmentos(
        @PathParam("id") Long id,
        @QueryParam("pregunta") String pregunta) {
        if (pregunta == null || pregunta.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "El parámetro 'pregunta' no puede estar vacío"))
                .build();
        }
        return Response.ok(busquedaSemanticaServicio.buscar(id, pregunta)).build();
    }
}
