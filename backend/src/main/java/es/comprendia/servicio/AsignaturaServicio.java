package es.comprendia.servicio;

import es.comprendia.dto.*;
import es.comprendia.entidad.Asignatura;
import es.comprendia.entidad.Video;
import es.comprendia.repositorio.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.*;

@ApplicationScoped
public class AsignaturaServicio {

    @Inject AsignaturaRepositorio asignaturaRepositorio;
    @Inject VideoRepositorio videoRepositorio;
    @Inject FragmentoTranscripcionRepositorio fragmentoRepositorio;
    @Inject EmbeddingServicio embeddingServicio;
    @Inject VideoConsultaServicio videoConsultaServicio;
    @Inject EntityManager entityManager;

    @Transactional
    public List<AsignaturaResumenDTO> obtenerTodas() {
        List<Asignatura> asignaturas = asignaturaRepositorio.buscarTodasOrdenadasPorNombre();
        List<Long> ids = asignaturas.stream().map(a -> a.id).toList();
        Map<Long, Long> conteoClases = contarClasesPorAsignaturas(ids);
        return asignaturas.stream()
            .map(a -> new AsignaturaResumenDTO(
                a.id, a.nombre, a.descripcion, a.profesor,
                conteoClases.getOrDefault(a.id, 0L),
                a.fechaActualizacion
            )).toList();
    }

    @Transactional
    public AsignaturaResumenDTO crear(SolicitudAsignaturaDTO solicitud) {
        if (solicitud.nombre() == null || solicitud.nombre().isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }
        Asignatura asignatura = new Asignatura();
        asignatura.nombre = solicitud.nombre().strip();
        asignatura.descripcion = solicitud.descripcion() != null ? solicitud.descripcion().strip() : null;
        asignatura.profesor = solicitud.profesor() != null ? solicitud.profesor().strip() : null;
        asignatura.fechaCreacion = LocalDateTime.now();
        asignatura.fechaActualizacion = LocalDateTime.now();
        asignaturaRepositorio.persist(asignatura);
        return new AsignaturaResumenDTO(asignatura.id, asignatura.nombre, asignatura.descripcion, asignatura.profesor, 0L, asignatura.fechaActualizacion);
    }

    @Transactional
    public AsignaturaDetalleDTO obtenerDetalle(Long id) {
        Asignatura asignatura = asignaturaRepositorio.findById(id);
        if (asignatura == null) throw new NotFoundException("Asignatura no encontrada");

        List<VideoResumenDTO> clases = videoConsultaServicio.obtenerVideosPorAsignatura(id);
        List<Long> idsVideo = clases.stream().map(VideoResumenDTO::id).toList();

        long conceptosTotales = idsVideo.isEmpty() ? 0L :
            entityManager.createQuery("SELECT COUNT(c) FROM ConceptoClaveVideo c WHERE c.video.id IN :ids", Long.class)
                .setParameter("ids", idsVideo).getSingleResult();

        long totalFragmentos = idsVideo.isEmpty() ? 0L :
            entityManager.createQuery("SELECT COUNT(f) FROM FragmentoTranscripcion f WHERE f.video.id IN :ids", Long.class)
                .setParameter("ids", idsVideo).getSingleResult();
        double horasProcesadas = Math.round((totalFragmentos * 6.0 / 3600.0) * 10.0) / 10.0;

        return new AsignaturaDetalleDTO(
            asignatura.id, asignatura.nombre, asignatura.descripcion, asignatura.profesor,
            clases.size(), conceptosTotales, horasProcesadas,
            asignatura.fechaActualizacion, clases
        );
    }

    @Transactional
    public AsignaturaResumenDTO actualizar(Long id, SolicitudAsignaturaDTO solicitud) {
        Asignatura asignatura = asignaturaRepositorio.findById(id);
        if (asignatura == null) throw new NotFoundException("Asignatura no encontrada");
        if (solicitud.nombre() != null && !solicitud.nombre().isBlank()) {
            asignatura.nombre = solicitud.nombre().strip();
        }
        if (solicitud.descripcion() != null) asignatura.descripcion = solicitud.descripcion().strip();
        if (solicitud.profesor() != null) asignatura.profesor = solicitud.profesor().strip();
        asignatura.fechaActualizacion = LocalDateTime.now();

        long numClases = videoRepositorio.count("asignaturaObj.id", id);
        return new AsignaturaResumenDTO(asignatura.id, asignatura.nombre, asignatura.descripcion, asignatura.profesor, numClases, asignatura.fechaActualizacion);
    }

    @Transactional
    public void eliminar(Long id, String confirmacionNombre) {
        Asignatura asignatura = asignaturaRepositorio.findById(id);
        if (asignatura == null) throw new NotFoundException("Asignatura no encontrada");
        if (!asignatura.nombre.equals(confirmacionNombre)) {
            throw new IllegalArgumentException("El nombre de confirmación no coincide");
        }

        List<Video> videos = videoRepositorio.list("asignaturaObj.id", id);
        for (Video video : videos) {
            entityManager.createQuery("DELETE FROM ConceptoClaveVideo c WHERE c.video.id = :id").setParameter("id", video.id).executeUpdate();
            entityManager.createQuery("DELETE FROM CapituloVideo c WHERE c.video.id = :id").setParameter("id", video.id).executeUpdate();
            entityManager.createQuery("DELETE FROM FragmentoTranscripcion f WHERE f.video.id = :id").setParameter("id", video.id).executeUpdate();
            videoRepositorio.delete(video);
        }
        asignaturaRepositorio.delete(asignatura);
    }

    @Transactional
    public List<ResultadoBusquedaAsignaturaDTO> buscarEnAsignatura(Long id, String pregunta) {
        Asignatura asignatura = asignaturaRepositorio.findById(id);
        if (asignatura == null) throw new NotFoundException("Asignatura no encontrada");

        List<Long> idsVideo = videoRepositorio.list("asignaturaObj.id", id)
            .stream().map(v -> v.id).toList();
        if (idsVideo.isEmpty()) return List.of();

        List<Double> vector = embeddingServicio.generarEmbedding(pregunta);
        String embeddingStr = vector.toString().replace(" ", "");
        return fragmentoRepositorio.buscarEnAsignatura(idsVideo, embeddingStr, 10);
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Long> contarClasesPorAsignaturas(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        List<Object[]> filas = entityManager
            .createQuery("SELECT v.asignaturaObj.id, COUNT(v) FROM Video v WHERE v.asignaturaObj.id IN :ids GROUP BY v.asignaturaObj.id")
            .setParameter("ids", ids)
            .getResultList();
        Map<Long, Long> resultado = new HashMap<>();
        for (Object[] fila : filas) resultado.put((Long) fila[0], (Long) fila[1]);
        return resultado;
    }
}
