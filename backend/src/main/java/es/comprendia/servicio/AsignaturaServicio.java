package es.comprendia.servicio;

import es.comprendia.dto.*;
import es.comprendia.entidad.Asignatura;
import es.comprendia.entidad.NotaConceptoAsignatura;
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
    @Inject ProfesorRepositorio profesorRepositorio;
    @Inject NotaConceptoAsignaturaRepositorio notaRepositorio;
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
        aplicarProfesor(asignatura, solicitud.idProfesor());
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
        aplicarProfesor(asignatura, solicitud.idProfesor());
        asignatura.fechaActualizacion = LocalDateTime.now();

        long numClases = videoRepositorio.count("asignaturaObj.id", id);
        return new AsignaturaResumenDTO(asignatura.id, asignatura.nombre, asignatura.descripcion, asignatura.profesor, numClases, asignatura.fechaActualizacion);
    }

    // Vincula la asignatura a un Profesor existente (si idProfesor no es null) y
    // sincroniza el nombre en el campo de texto para compatibilidad.
    private void aplicarProfesor(Asignatura asignatura, Long idProfesor) {
        if (idProfesor == null) return;
        es.comprendia.entidad.Profesor profesor = profesorRepositorio.findById(idProfesor);
        if (profesor == null) {
            throw new NotFoundException("Profesor no encontrado");
        }
        asignatura.profesorObj = profesor;
        asignatura.profesor = profesor.nombre;
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

    // Conceptos agregados de toda la asignatura, agrupados por nombre (sin distinguir
    // mayúsculas). Cada grupo lista las clases donde aparece, con su definición y momento.
    @Transactional
    public List<ConceptoCursoDTO> obtenerConceptosCurso(Long id) {
        Asignatura asignatura = asignaturaRepositorio.findById(id);
        if (asignatura == null) throw new NotFoundException("Asignatura no encontrada");

        List<Long> idsVideo = videoRepositorio.list("asignaturaObj.id", id)
            .stream().map(v -> v.id).toList();
        if (idsVideo.isEmpty()) return List.of();

        @SuppressWarnings("unchecked")
        List<Object[]> filas = entityManager
            .createQuery(
                "SELECT c.video.id, c.video.titulo, c.nombre, c.definicion, c.tiempoInicio, " +
                "c.tiempoFin, c.creadoManual, c.generadoPorIa " +
                "FROM ConceptoClaveVideo c WHERE c.video.id IN :ids " +
                "ORDER BY c.nombre ASC, c.tiempoInicio ASC")
            .setParameter("ids", idsVideo)
            .getResultList();

        // Agrupar por nombre normalizado, conservando el primer nombre visto como etiqueta
        Map<String, String> etiquetas = new LinkedHashMap<>();
        Map<String, List<ConceptoClaseAparicionDTO>> grupos = new LinkedHashMap<>();

        for (Object[] fila : filas) {
            String nombre = (String) fila[2];
            if (nombre == null || nombre.isBlank()) continue;
            String clave = nombre.strip().toLowerCase();
            etiquetas.putIfAbsent(clave, nombre.strip());
            grupos.computeIfAbsent(clave, k -> new ArrayList<>()).add(new ConceptoClaseAparicionDTO(
                (Long) fila[0],
                (String) fila[1],
                (String) fila[3],
                (Double) fila[4],
                (Double) fila[5],
                (Boolean) fila[6],
                (Boolean) fila[7]
            ));
        }

        return grupos.entrySet().stream()
            .map(e -> new ConceptoCursoDTO(etiquetas.get(e.getKey()), e.getValue().size(), e.getValue()))
            .sorted(Comparator.comparingLong(ConceptoCursoDTO::totalApariciones).reversed()
                .thenComparing(ConceptoCursoDTO::nombre, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    // Nota personal global de un concepto dentro de la asignatura. Si no existe, devuelve vacía.
    @Transactional
    public NotaConceptoDTO obtenerNotaConcepto(Long idAsignatura, String nombreConcepto) {
        if (asignaturaRepositorio.findById(idAsignatura) == null) {
            throw new NotFoundException("Asignatura no encontrada");
        }
        return notaRepositorio.buscar(idAsignatura, nombreConcepto)
            .map(n -> new NotaConceptoDTO(n.nombreConcepto, n.nota, n.fechaActualizacion))
            .orElse(new NotaConceptoDTO(nombreConcepto, "", null));
    }

    // Crea o actualiza la nota global del concepto (una sola por asignatura + nombre).
    @Transactional
    public NotaConceptoDTO guardarNotaConcepto(Long idAsignatura, String nombreConcepto, String nota) {
        if (asignaturaRepositorio.findById(idAsignatura) == null) {
            throw new NotFoundException("Asignatura no encontrada");
        }
        if (nombreConcepto == null || nombreConcepto.isBlank()) {
            throw new IllegalArgumentException("El nombre del concepto es obligatorio");
        }

        NotaConceptoAsignatura entidad = notaRepositorio.buscar(idAsignatura, nombreConcepto)
            .orElseGet(() -> {
                NotaConceptoAsignatura nueva = new NotaConceptoAsignatura();
                nueva.asignaturaId = idAsignatura;
                nueva.nombreConcepto = nombreConcepto.strip();
                notaRepositorio.persist(nueva);
                return nueva;
            });
        entidad.nota = nota != null ? nota : "";
        entidad.fechaActualizacion = LocalDateTime.now();
        return new NotaConceptoDTO(entidad.nombreConcepto, entidad.nota, entidad.fechaActualizacion);
    }

    // Edita un concepto agrupado: actualiza el nombre y/o la definición de TODAS sus
    // apariciones dentro de las clases de esta asignatura. No toca otras asignaturas,
    // ni fragmentos, ni vídeos, ni embeddings.
    @Transactional
    public void editarConceptoCurso(Long idAsignatura, String nombreConcepto, String nuevoNombre, String definicion) {
        if (asignaturaRepositorio.findById(idAsignatura) == null) {
            throw new NotFoundException("Asignatura no encontrada");
        }
        List<Long> idsVideo = videoRepositorio.list("asignaturaObj.id", idAsignatura)
            .stream().map(v -> v.id).toList();
        if (idsVideo.isEmpty()) return;

        String nombreFinal = (nuevoNombre != null && !nuevoNombre.isBlank())
            ? nuevoNombre.strip() : nombreConcepto;

        StringBuilder jpql = new StringBuilder(
            "UPDATE ConceptoClaveVideo c SET c.nombre = :nombre");
        if (definicion != null) jpql.append(", c.definicion = :def");
        jpql.append(" WHERE LOWER(c.nombre) = LOWER(:viejo) AND c.video.id IN :ids");

        var query = entityManager.createQuery(jpql.toString())
            .setParameter("nombre", nombreFinal)
            .setParameter("viejo", nombreConcepto)
            .setParameter("ids", idsVideo);
        if (definicion != null) query.setParameter("def", definicion);
        query.executeUpdate();

        // Si cambió el nombre, renombrar también su nota asociada para no perderla
        if (!nombreFinal.equalsIgnoreCase(nombreConcepto)) {
            notaRepositorio.buscar(idAsignatura, nombreConcepto)
                .ifPresent(n -> n.nombreConcepto = nombreFinal);
        }
    }

    // Elimina un concepto agrupado: borra TODAS sus apariciones en las clases de esta
    // asignatura y su nota asociada. Solo afecta a ConceptoClaveVideo (no fragmentos/vídeos).
    @Transactional
    public void eliminarConceptoCurso(Long idAsignatura, String nombreConcepto) {
        if (asignaturaRepositorio.findById(idAsignatura) == null) {
            throw new NotFoundException("Asignatura no encontrada");
        }
        List<Long> idsVideo = videoRepositorio.list("asignaturaObj.id", idAsignatura)
            .stream().map(v -> v.id).toList();
        if (!idsVideo.isEmpty()) {
            entityManager.createQuery(
                "DELETE FROM ConceptoClaveVideo c WHERE LOWER(c.nombre) = LOWER(:nombre) AND c.video.id IN :ids")
                .setParameter("nombre", nombreConcepto)
                .setParameter("ids", idsVideo)
                .executeUpdate();
        }
        // Borrar la nota asociada (si existe)
        notaRepositorio.buscar(idAsignatura, nombreConcepto).ifPresent(notaRepositorio::delete);
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
