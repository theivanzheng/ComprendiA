package es.comprendia.recurso;

import es.comprendia.dto.FragmentoDTO;
import es.comprendia.dto.RespuestaRagDTO;
import es.comprendia.dto.ResultadoBusquedaDTO;
import es.comprendia.dto.VideoResumenDTO;
import es.comprendia.repositorio.VideoRepositorio;
import es.comprendia.servicio.BusquedaSemanticaServicio;
import es.comprendia.servicio.FragmentoConsultaServicio;
import es.comprendia.servicio.RagServicio;
import es.comprendia.servicio.VideoConsultaServicio;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
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

    @Inject
    RagServicio ragServicio;

    @Inject
    VideoRepositorio videoRepositorio;

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

    @PATCH
    @Path("/{id}/titulo")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response actualizarTitulo(@PathParam("id") Long id, Map<String, String> cuerpo) {
        String titulo = cuerpo == null ? null : cuerpo.get("titulo");
        if (titulo == null || titulo.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "El título no puede estar vacío"))
                .build();
        }
        var video = videoRepositorio.findById(id);
        if (video == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Vídeo no encontrado"))
                .build();
        }
        video.titulo = titulo.strip();
        return Response.ok().build();
    }

    @GET
    @Path("/{id}/responder")
    public Response responder(
        @PathParam("id") Long id,
        @QueryParam("pregunta") String pregunta) {
        if (pregunta == null || pregunta.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "El parámetro 'pregunta' no puede estar vacío"))
                .build();
        }
        try {
            RespuestaRagDTO respuesta = ragServicio.responder(id, pregunta);
            return Response.ok(respuesta).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
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
