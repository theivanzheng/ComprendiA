package es.comprendia.servicio;

import es.comprendia.dto.ProfesorDTO;
import es.comprendia.dto.SolicitudProfesorDTO;
import es.comprendia.entidad.Profesor;
import es.comprendia.repositorio.ProfesorRepositorio;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ProfesorServicio {

    @Inject
    ProfesorRepositorio profesorRepositorio;

    @Inject
    EntityManager entityManager;

    @Transactional
    public List<ProfesorDTO> obtenerTodos() {
        List<Profesor> profesores = profesorRepositorio.buscarTodosOrdenados();
        List<Long> ids = profesores.stream().map(p -> p.id).toList();
        Map<Long, Long> conteo = contarAsignaturasPorProfesor(ids);
        return profesores.stream()
            .map(p -> new ProfesorDTO(
                p.id, p.nombre, p.email,
                conteo.getOrDefault(p.id, 0L),
                p.fechaCreacion, p.fechaActualizacion))
            .toList();
    }

    @Transactional
    public ProfesorDTO crear(SolicitudProfesorDTO solicitud) {
        if (solicitud == null || solicitud.nombre() == null || solicitud.nombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del profesor es obligatorio");
        }
        String nombre = solicitud.nombre().strip();

        // Reutilizar si ya existe un profesor con ese nombre (evita duplicados)
        Profesor profesor = profesorRepositorio.buscarPorNombre(nombre).orElse(null);
        if (profesor == null) {
            profesor = new Profesor();
            profesor.nombre = nombre;
            profesor.fechaCreacion = LocalDateTime.now();
        }
        if (solicitud.email() != null && !solicitud.email().isBlank()) {
            profesor.email = solicitud.email().strip();
        }
        profesor.fechaActualizacion = LocalDateTime.now();
        profesorRepositorio.persist(profesor);

        return new ProfesorDTO(profesor.id, profesor.nombre, profesor.email, 0L,
            profesor.fechaCreacion, profesor.fechaActualizacion);
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Long> contarAsignaturasPorProfesor(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        List<Object[]> filas = entityManager
            .createQuery("SELECT a.profesorObj.id, COUNT(a) FROM Asignatura a WHERE a.profesorObj.id IN :ids GROUP BY a.profesorObj.id")
            .setParameter("ids", ids)
            .getResultList();
        Map<Long, Long> resultado = new HashMap<>();
        for (Object[] fila : filas) resultado.put((Long) fila[0], (Long) fila[1]);
        return resultado;
    }
}
