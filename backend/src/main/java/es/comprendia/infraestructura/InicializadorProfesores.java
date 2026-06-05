package es.comprendia.infraestructura;

import es.comprendia.entidad.Asignatura;
import es.comprendia.entidad.Profesor;
import es.comprendia.entidad.Video;
import es.comprendia.repositorio.AsignaturaRepositorio;
import es.comprendia.repositorio.ProfesorRepositorio;
import es.comprendia.repositorio.VideoRepositorio;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Al arrancar, migra los profesores guardados como texto libre (Video.profesor y
 * Asignatura.profesor) al nuevo modelo relacional (entidad Profesor). Crea el Profesor
 * si no existe y vincula vídeo/asignatura. No borra datos antiguos: conserva el String.
 * Prioridad alta para ejecutarse de forma estable junto a la migración de asignaturas.
 */
@ApplicationScoped
public class InicializadorProfesores {

    private static final Logger LOG = Logger.getLogger(InicializadorProfesores.class);
    private static final String PROFESOR_PENDIENTE = "Profesor pendiente";

    @Inject
    VideoRepositorio videoRepositorio;

    @Inject
    AsignaturaRepositorio asignaturaRepositorio;

    @Inject
    ProfesorRepositorio profesorRepositorio;

    @Transactional
    void migrar(@Observes @Priority(2000) StartupEvent ev) {
        Map<String, Profesor> cache = new HashMap<>();
        int vinculados = 0;

        // 1) Vídeos con profesor en texto pero sin relación
        List<Video> videos = videoRepositorio.list(
            "profesorObj IS NULL AND profesor IS NOT NULL AND profesor != '' AND profesor != ?1",
            PROFESOR_PENDIENTE);
        for (Video video : videos) {
            Profesor profesor = obtenerOCrear(cache, video.profesor);
            if (profesor != null) {
                video.profesorObj = profesor;
                video.profesor = profesor.nombre;
                vinculados++;
            }
        }

        // 2) Asignaturas con profesor en texto pero sin relación
        List<Asignatura> asignaturas = asignaturaRepositorio.list(
            "profesorObj IS NULL AND profesor IS NOT NULL AND profesor != '' AND profesor != ?1",
            PROFESOR_PENDIENTE);
        for (Asignatura asignatura : asignaturas) {
            Profesor profesor = obtenerOCrear(cache, asignatura.profesor);
            if (profesor != null) {
                asignatura.profesorObj = profesor;
                asignatura.profesor = profesor.nombre;
                vinculados++;
            }
        }

        if (vinculados > 0) {
            LOG.infof("[Migración] %d profesores vinculados al modelo relacional", vinculados);
        } else {
            LOG.debug("[Migración] No hay profesores en texto pendientes de migrar");
        }
    }

    private Profesor obtenerOCrear(Map<String, Profesor> cache, String nombreTexto) {
        if (nombreTexto == null) return null;
        String nombre = nombreTexto.strip();
        if (nombre.isBlank() || nombre.equalsIgnoreCase(PROFESOR_PENDIENTE)) return null;

        return cache.computeIfAbsent(nombre, n ->
            profesorRepositorio.buscarPorNombre(n).orElseGet(() -> {
                Profesor nuevo = new Profesor();
                nuevo.nombre = n;
                nuevo.fechaCreacion = LocalDateTime.now();
                nuevo.fechaActualizacion = LocalDateTime.now();
                profesorRepositorio.persist(nuevo);
                LOG.infof("[Migración] Creado profesor '%s'", n);
                return nuevo;
            }));
    }
}
