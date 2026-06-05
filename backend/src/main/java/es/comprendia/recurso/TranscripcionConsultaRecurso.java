package es.comprendia.recurso;

import es.comprendia.dto.ConsultaConversacionDTO;
import es.comprendia.dto.FragmentoDTO;
import es.comprendia.dto.RespuestaRagDTO;
import es.comprendia.dto.ResultadoBusquedaDTO;
import es.comprendia.dto.SolicitudCapituloDTO;
import es.comprendia.dto.SolicitudConceptoDTO;
import es.comprendia.dto.VideoMetadataDTO;
import es.comprendia.dto.VideoResumenDTO;
import es.comprendia.repositorio.AsignaturaRepositorio;
import es.comprendia.repositorio.CapituloVideoRepositorio;
import es.comprendia.repositorio.ConceptoClaveVideoRepositorio;
import es.comprendia.repositorio.ProfesorRepositorio;
import es.comprendia.repositorio.VideoRepositorio;
import es.comprendia.servicio.BusquedaSemanticaServicio;
import es.comprendia.servicio.CapituloConceptoServicio;
import es.comprendia.servicio.FragmentoConsultaServicio;
import es.comprendia.servicio.RagServicio;
import es.comprendia.servicio.VideoConsultaServicio;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
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
    es.comprendia.servicio.TranscripcionPersistenciaServicio transcripcionPersistenciaServicio;

    @Inject
    FragmentoConsultaServicio fragmentoConsultaServicio;

    @Inject
    BusquedaSemanticaServicio busquedaSemanticaServicio;

    @Inject
    RagServicio ragServicio;

    @Inject
    VideoRepositorio videoRepositorio;

    @Inject
    AsignaturaRepositorio asignaturaRepositorio;

    @Inject
    ProfesorRepositorio profesorRepositorio;

    @Inject
    CapituloVideoRepositorio capituloVideoRepositorio;

    @Inject
    ConceptoClaveVideoRepositorio conceptoClaveVideoRepositorio;

    @Inject
    CapituloConceptoServicio capituloConceptoServicio;

    @GET
    public List<VideoResumenDTO> obtenerTranscripciones(
        @QueryParam("page") @DefaultValue("0") int pagina,
        @QueryParam("size") @DefaultValue("10") int tamanyo) {
        return videoConsultaServicio.obtenerVideos(pagina, tamanyo);
    }

    @GET
    @Path("/{id}")
    public Response obtenerTranscripcion(@PathParam("id") Long id) {
        VideoResumenDTO video = videoConsultaServicio.obtenerVideo(id);
        if (video == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Vídeo no encontrado"))
                .build();
        }
        return Response.ok(video).build();
    }

    @GET
    @Path("/{id}/fragmentos")
    public List<FragmentoDTO> obtenerFragmentos(@PathParam("id") Long id) {
        return fragmentoConsultaServicio.obtenerPorVideoId(id);
    }

    @GET
    @Path("/{id}/capitulos")
    public Response obtenerCapitulos(@PathParam("id") Long id) {
        if (videoRepositorio.findById(id) == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Vídeo no encontrado"))
                .build();
        }
        return Response.ok(capituloVideoRepositorio.buscarPorVideoOrdenado(id)).build();
    }

    @POST
    @Path("/{id}/capitulos")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response crearCapitulo(@PathParam("id") Long id, SolicitudCapituloDTO solicitud) {
        try {
            return Response.status(Response.Status.CREATED)
                .entity(capituloConceptoServicio.crearCapitulo(id, solicitud)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @GET
    @Path("/{id}/conceptos")
    public Response obtenerConceptos(@PathParam("id") Long id) {
        if (videoRepositorio.findById(id) == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Vídeo no encontrado"))
                .build();
        }
        return Response.ok(conceptoClaveVideoRepositorio.buscarPorVideoOrdenado(id)).build();
    }

    @POST
    @Path("/{id}/conceptos")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response crearConcepto(@PathParam("id") Long id, SolicitudConceptoDTO solicitud) {
        try {
            return Response.status(Response.Status.CREATED)
                .entity(capituloConceptoServicio.crearConcepto(id, solicitud)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response eliminarTranscripcion(@PathParam("id") Long id) {
        if (videoRepositorio.findById(id) == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Vídeo no encontrado"))
                .build();
        }
        transcripcionPersistenciaServicio.eliminarVideoCompleto(id);
        return Response.noContent().build();
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

    @PATCH
    @Path("/{id}/metadata")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response actualizarMetadata(@PathParam("id") Long id, VideoMetadataDTO metadata) {
        var video = videoRepositorio.findById(id);
        if (video == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Vídeo no encontrado"))
                .build();
        }

        if (metadata == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "La metadata no puede estar vacía"))
                .build();
        }

        if (metadata.asignatura() != null) {
            video.asignatura = normalizarMetadata(metadata.asignatura(), "Sin asignatura");
        }
        if (metadata.profesor() != null) {
            video.profesor = normalizarMetadata(metadata.profesor(), "Profesor pendiente");
        }
        if (metadata.fechaClase() != null) {
            video.fechaClase = metadata.fechaClase();
        }
        if (metadata.completado() != null) {
            video.completado = metadata.completado();
        }

        if (metadata.resumen() != null) {
            video.resumen = metadata.resumen().isBlank() ? null : metadata.resumen().strip();
        }

        if (metadata.idAsignatura() != null) {
            es.comprendia.entidad.Asignatura asignatura = asignaturaRepositorio.findById(metadata.idAsignatura());
            if (asignatura == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Asignatura no encontrada"))
                    .build();
            }
            video.asignaturaObj = asignatura;
            video.asignatura = asignatura.nombre;
            // Elección manual: deja de ser una sugerencia.
            video.asignaturaSugerida = false;
            video.criterioAsignacion = es.comprendia.entidad.CriterioAsignacion.MANUAL;
            // Aprendizaje: si el vídeo tiene canal y la asignatura aún no, asociarlo para
            // que futuros vídeos del mismo canal se clasifiquen automáticamente por canal.
            if (video.canalYoutubeId != null && !video.canalYoutubeId.isBlank()
                && (asignatura.canalYoutubeId == null || asignatura.canalYoutubeId.isBlank())) {
                asignatura.canalYoutubeId = video.canalYoutubeId;
                asignatura.canalYoutubeNombre = video.canalYoutubeNombre;
            }
            asignatura.fechaActualizacion = java.time.LocalDateTime.now();
        }

        if (metadata.idProfesor() != null) {
            es.comprendia.entidad.Profesor profesor = profesorRepositorio.findById(metadata.idProfesor());
            if (profesor == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Profesor no encontrado"))
                    .build();
            }
            video.profesorObj = profesor;
            video.profesor = profesor.nombre;
        }

        return Response.ok(videoConsultaServicio.obtenerVideo(id)).build();
    }

    private String normalizarMetadata(String valor, String valorPorDefecto) {
        if (valor == null || valor.isBlank()) {
            return valorPorDefecto;
        }
        return valor.strip();
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

    @POST
    @Path("/{id}/conversar")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response conversar(
        @PathParam("id") Long id,
        ConsultaConversacionDTO consulta) {
        if (consulta == null || consulta.pregunta() == null || consulta.pregunta().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "El campo 'pregunta' no puede estar vacío"))
                .build();
        }
        try {
            RespuestaRagDTO respuesta = ragServicio.responderConversacion(id, consulta);
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
