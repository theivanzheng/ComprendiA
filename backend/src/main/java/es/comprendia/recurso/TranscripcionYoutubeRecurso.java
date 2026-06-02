package es.comprendia.recurso;

import es.comprendia.dto.EstadoTrabajoDTO;
import es.comprendia.dto.SolicitudYoutubeDTO;
import es.comprendia.servicio.TrabajoServicio;
import es.comprendia.servicio.TranscripcionYoutubeServicio;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.Map;

@Path("/api/transcripciones/youtube")
public class TranscripcionYoutubeRecurso {

    private static final Logger LOG = Logger.getLogger(TranscripcionYoutubeRecurso.class);

    @Inject
    TranscripcionYoutubeServicio transcripcionYoutubeServicio;

    @Inject
    TrabajoServicio trabajoServicio;

    @POST
    @Blocking
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response iniciarTranscripcion(SolicitudYoutubeDTO solicitud) {
        if (solicitud == null || solicitud.getUrlVideo() == null) {
            return error400("El campo urlVideo es obligatorio");
        }
        try {
            transcripcionYoutubeServicio.validarUrlYoutube(solicitud.getUrlVideo());
        } catch (IllegalArgumentException e) {
            return error400(e.getMessage());
        }

        String idTrabajo = trabajoServicio.crearTrabajo();
        String urlVideo = solicitud.getUrlVideo();

        Thread.ofVirtual().start(() -> {
            try {
                var resultado = transcripcionYoutubeServicio.procesarUrlYoutube(
                    urlVideo,
                    fase -> trabajoServicio.actualizarFase(idTrabajo, fase)
                );
                trabajoServicio.completar(idTrabajo, resultado);
            } catch (Exception e) {
                LOG.errorf(e, "[Trabajo %s] Error en pipeline: %s", idTrabajo, e.getMessage());
                trabajoServicio.marcarError(idTrabajo,
                    e.getMessage() != null ? e.getMessage() : "Error interno del servidor");
            }
        });

        return Response.accepted(Map.of("idTrabajo", idTrabajo)).build();
    }

    @GET
    @Path("/{idTrabajo}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response consultarEstado(@PathParam("idTrabajo") String idTrabajo) {
        return trabajoServicio.obtener(idTrabajo)
            .map(estado -> Response.ok(estado).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Trabajo no encontrado: " + idTrabajo))
                .build());
    }

    private Response error400(String mensaje) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(Map.of("error", mensaje))
            .build();
    }
}
