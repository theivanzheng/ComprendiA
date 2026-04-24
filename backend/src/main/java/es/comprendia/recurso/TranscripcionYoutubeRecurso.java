package es.comprendia.recurso;

import es.comprendia.dto.RespuestaTranscripcionDTO;
import es.comprendia.dto.SolicitudYoutubeDTO;
import es.comprendia.servicio.TranscripcionYoutubeServicio;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Blocking
@Path("/api/transcripciones/youtube")
public class TranscripcionYoutubeRecurso {

    @Inject
    TranscripcionYoutubeServicio transcripcionYoutubeServicio;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response transcribirDesdeYoutube(SolicitudYoutubeDTO solicitud) {
        if (solicitud == null || solicitud.getUrlVideo() == null) {
            return error("El campo urlVideo es obligatorio");
        }
        try {
            RespuestaTranscripcionDTO respuesta =
                transcripcionYoutubeServicio.procesarUrlYoutube(solicitud.getUrlVideo());
            return Response.ok(respuesta).build();
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    private Response error(String mensaje) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(Map.of("error", mensaje))
            .build();
    }
}
