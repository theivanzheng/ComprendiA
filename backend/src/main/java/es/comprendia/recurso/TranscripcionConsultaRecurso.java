package es.comprendia.recurso;

import es.comprendia.dto.FragmentoDTO;
import es.comprendia.dto.VideoResumenDTO;
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

import java.util.List;

@Blocking
@Path("/api/transcripciones")
@Produces(MediaType.APPLICATION_JSON)
public class TranscripcionConsultaRecurso {

    @Inject
    VideoConsultaServicio videoConsultaServicio;

    @Inject
    FragmentoConsultaServicio fragmentoConsultaServicio;

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
}
