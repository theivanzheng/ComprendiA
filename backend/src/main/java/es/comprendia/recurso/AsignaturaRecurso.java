package es.comprendia.recurso;

import es.comprendia.dto.*;
import es.comprendia.servicio.AsignaturaServicio;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Blocking
@Path("/api/asignaturas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AsignaturaRecurso {

    @Inject
    AsignaturaServicio asignaturaServicio;

    @GET
    public List<AsignaturaResumenDTO> obtenerTodas() {
        return asignaturaServicio.obtenerTodas();
    }

    @POST
    @Transactional
    public Response crear(SolicitudAsignaturaDTO solicitud) {
        try {
            AsignaturaResumenDTO dto = asignaturaServicio.crear(solicitud);
            return Response.status(Response.Status.CREATED).entity(dto).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @GET
    @Path("/{id}")
    public Response obtenerDetalle(@PathParam("id") Long id) {
        try {
            return Response.ok(asignaturaServicio.obtenerDetalle(id)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @PATCH
    @Path("/{id}")
    @Transactional
    public Response actualizar(@PathParam("id") Long id, SolicitudAsignaturaDTO solicitud) {
        try {
            return Response.ok(asignaturaServicio.actualizar(id, solicitud)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response eliminar(@PathParam("id") Long id, Map<String, String> cuerpo) {
        String confirmacion = cuerpo != null ? cuerpo.get("confirmacionNombre") : null;
        if (confirmacion == null || confirmacion.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Se requiere confirmacionNombre en el body")).build();
        }
        try {
            asignaturaServicio.eliminar(id, confirmacion);
            return Response.noContent().build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @GET
    @Path("/{id}/buscar")
    public Response buscar(@PathParam("id") Long id, @QueryParam("pregunta") String pregunta) {
        if (pregunta == null || pregunta.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "El parámetro 'pregunta' no puede estar vacío")).build();
        }
        try {
            return Response.ok(asignaturaServicio.buscarEnAsignatura(id, pregunta)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @GET
    @Path("/{id}/conceptos")
    public Response conceptos(@PathParam("id") Long id) {
        try {
            return Response.ok(asignaturaServicio.obtenerConceptosCurso(id)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @PATCH
    @Path("/{id}/conceptos/{nombre}")
    @Transactional
    public Response editarConcepto(@PathParam("id") Long id, @PathParam("nombre") String nombre, SolicitudEditarConceptoDTO solicitud) {
        try {
            String nuevoNombre = solicitud != null ? solicitud.nuevoNombre() : null;
            String definicion = solicitud != null ? solicitud.definicion() : null;
            asignaturaServicio.editarConceptoCurso(id, nombre, nuevoNombre, definicion);
            return Response.noContent().build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/{id}/conceptos/{nombre}")
    @Transactional
    public Response eliminarConcepto(@PathParam("id") Long id, @PathParam("nombre") String nombre) {
        try {
            asignaturaServicio.eliminarConceptoCurso(id, nombre);
            return Response.noContent().build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @GET
    @Path("/{id}/conceptos/{nombre}/nota")
    public Response obtenerNota(@PathParam("id") Long id, @PathParam("nombre") String nombre) {
        try {
            return Response.ok(asignaturaServicio.obtenerNotaConcepto(id, nombre)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @PATCH
    @Path("/{id}/conceptos/{nombre}/nota")
    @Transactional
    public Response guardarNota(@PathParam("id") Long id, @PathParam("nombre") String nombre, SolicitudNotaDTO solicitud) {
        try {
            String nota = solicitud != null ? solicitud.nota() : "";
            return Response.ok(asignaturaServicio.guardarNotaConcepto(id, nombre, nota)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
        }
    }
}
