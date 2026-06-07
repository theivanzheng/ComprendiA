package es.comprendia.recurso;

import es.comprendia.servicio.DocumentoServicio;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

/**
 * Borrado de documentos del curso. La subida y el listado viven en
 * {@link TranscripcionConsultaRecurso} (bajo {@code /api/transcripciones/{id}/documentos}) para no
 * chocar con la resolución de recursos de JAX-RS: la clase con el {@code @Path} más específico gana,
 * así que los sub-recursos de una clase deben declararse en el recurso de transcripciones.
 */
@Path("/api/documentos")
public class DocumentoRecurso {

    @Inject
    DocumentoServicio documentoServicio;

    @DELETE
    @Path("/{id}")
    public void eliminar(@PathParam("id") Long idDocumento) {
        documentoServicio.eliminar(idDocumento);
    }
}
