package es.comprendia.infraestructura;

import es.comprendia.entidad.Asignatura;
import es.comprendia.entidad.Video;
import es.comprendia.repositorio.AsignaturaRepositorio;
import es.comprendia.repositorio.VideoRepositorio;
import io.quarkus.runtime.StartupEvent;
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
 * Al arrancar la aplicación, migra los vídeos que tienen asignatura como texto libre
 * (Video.asignatura) pero no tienen relación con una entidad Asignatura (asignaturaObj == null).
 * Crea la Asignatura si no existe y vincula el vídeo.
 */
@ApplicationScoped
public class InicializadorAsignaturas {

    private static final Logger LOG = Logger.getLogger(InicializadorAsignaturas.class);
    private static final String SIN_ASIGNATURA = "Sin asignatura";

    @Inject
    VideoRepositorio videoRepositorio;

    @Inject
    AsignaturaRepositorio asignaturaRepositorio;

    @Transactional
    void migrar(@Observes StartupEvent ev) {
        List<Video> videosSinRelacion = videoRepositorio
            .list("asignaturaObj IS NULL AND asignatura IS NOT NULL AND asignatura != '' AND asignatura != ?1",
                SIN_ASIGNATURA);

        if (videosSinRelacion.isEmpty()) {
            LOG.debug("[Migración] No hay vídeos con asignatura string sin relación — omitiendo");
            return;
        }

        LOG.infof("[Migración] Migrando asignaturas de %d vídeos al modelo relacional", videosSinRelacion.size());

        // Mapa nombre -> entidad Asignatura para evitar duplicar consultas
        Map<String, Asignatura> cache = new HashMap<>();

        for (Video video : videosSinRelacion) {
            String nombreNormalizado = video.asignatura.strip();
            if (nombreNormalizado.isBlank() || nombreNormalizado.equalsIgnoreCase(SIN_ASIGNATURA)) {
                continue;
            }

            Asignatura asignatura = cache.computeIfAbsent(nombreNormalizado, nombre -> {
                // Buscar existente (insensible a mayúsculas)
                return asignaturaRepositorio
                    .find("LOWER(nombre) = LOWER(?1)", nombre)
                    .firstResultOptional()
                    .orElseGet(() -> {
                        Asignatura nueva = new Asignatura();
                        nueva.nombre = nombre;
                        nueva.fechaCreacion = LocalDateTime.now();
                        nueva.fechaActualizacion = LocalDateTime.now();
                        asignaturaRepositorio.persist(nueva);
                        LOG.infof("[Migración] Creada asignatura '%s'", nombre);
                        return nueva;
                    });
            });

            video.asignaturaObj = asignatura;
            // Normalizar también el campo String al nombre oficial de la entidad
            video.asignatura = asignatura.nombre;
            LOG.debugf("[Migración] Vídeo id=%d vinculado a asignatura '%s'", video.id, asignatura.nombre);
        }

        LOG.infof("[Migración] Migración completada — %d vídeos actualizados", videosSinRelacion.size());
    }
}
