package es.comprendia.servicio;

import es.comprendia.dto.CapituloVideoDTO;
import es.comprendia.dto.ConceptoClaveVideoDTO;
import es.comprendia.dto.SolicitudCapituloDTO;
import es.comprendia.dto.SolicitudConceptoDTO;
import es.comprendia.entidad.CapituloVideo;
import es.comprendia.entidad.ConceptoClaveVideo;
import es.comprendia.entidad.Video;
import es.comprendia.repositorio.CapituloVideoRepositorio;
import es.comprendia.repositorio.ConceptoClaveVideoRepositorio;
import es.comprendia.repositorio.VideoRepositorio;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

/**
 * Altas, ediciones y bajas manuales de capítulos y conceptos clave.
 * Los registros creados aquí quedan marcados como creadoManual=true.
 */
@ApplicationScoped
public class CapituloConceptoServicio {

    @Inject
    VideoRepositorio videoRepositorio;

    @Inject
    CapituloVideoRepositorio capituloRepositorio;

    @Inject
    ConceptoClaveVideoRepositorio conceptoRepositorio;

    // ── Capítulos ───────────────────────────────────────────────────────────

    @Transactional
    public CapituloVideoDTO crearCapitulo(Long idVideo, SolicitudCapituloDTO solicitud) {
        Video video = videoRepositorio.findById(idVideo);
        if (video == null) throw new NotFoundException("Vídeo no encontrado");
        if (solicitud == null || solicitud.titulo() == null || solicitud.titulo().isBlank()) {
            throw new IllegalArgumentException("El título del capítulo es obligatorio");
        }

        CapituloVideo capitulo = new CapituloVideo();
        capitulo.video = video;
        capitulo.titulo = solicitud.titulo().strip();
        capitulo.descripcion = solicitud.descripcion() != null ? solicitud.descripcion().strip() : null;
        capitulo.tiempoInicio = solicitud.tiempoInicio() != null ? solicitud.tiempoInicio() : 0.0;
        capitulo.tiempoFin = solicitud.tiempoFin();
        capitulo.ordenCapitulo = siguienteOrdenCapitulo(idVideo);
        capitulo.origen = "MANUAL";
        capitulo.creadoManual = true;
        capitulo.generadoPorIa = false;
        capituloRepositorio.persist(capitulo);

        return aDto(capitulo);
    }

    @Transactional
    public CapituloVideoDTO actualizarCapitulo(Long idCapitulo, SolicitudCapituloDTO solicitud) {
        CapituloVideo capitulo = capituloRepositorio.findById(idCapitulo);
        if (capitulo == null) throw new NotFoundException("Capítulo no encontrado");
        if (solicitud == null) throw new IllegalArgumentException("Datos vacíos");

        if (solicitud.titulo() != null && !solicitud.titulo().isBlank()) {
            capitulo.titulo = solicitud.titulo().strip();
        }
        if (solicitud.descripcion() != null) capitulo.descripcion = solicitud.descripcion().strip();
        if (solicitud.tiempoInicio() != null) capitulo.tiempoInicio = solicitud.tiempoInicio();
        if (solicitud.tiempoFin() != null) capitulo.tiempoFin = solicitud.tiempoFin();
        return aDto(capitulo);
    }

    @Transactional
    public void eliminarCapitulo(Long idCapitulo) {
        CapituloVideo capitulo = capituloRepositorio.findById(idCapitulo);
        if (capitulo == null) throw new NotFoundException("Capítulo no encontrado");
        capituloRepositorio.delete(capitulo);
    }

    // ── Conceptos ───────────────────────────────────────────────────────────

    @Transactional
    public ConceptoClaveVideoDTO crearConcepto(Long idVideo, SolicitudConceptoDTO solicitud) {
        Video video = videoRepositorio.findById(idVideo);
        if (video == null) throw new NotFoundException("Vídeo no encontrado");
        if (solicitud == null || solicitud.nombre() == null || solicitud.nombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del concepto es obligatorio");
        }

        ConceptoClaveVideo concepto = new ConceptoClaveVideo();
        concepto.video = video;
        concepto.nombre = solicitud.nombre().strip();
        concepto.definicion = solicitud.definicion() != null ? solicitud.definicion().strip() : null;
        concepto.tiempoInicio = solicitud.tiempoInicio() != null ? solicitud.tiempoInicio() : 0.0;
        concepto.tiempoFin = solicitud.tiempoFin();
        concepto.ordenConcepto = siguienteOrdenConcepto(idVideo);
        concepto.creadoManual = true;
        concepto.generadoPorIa = false;
        conceptoRepositorio.persist(concepto);

        return aDto(concepto);
    }

    @Transactional
    public ConceptoClaveVideoDTO actualizarConcepto(Long idConcepto, SolicitudConceptoDTO solicitud) {
        ConceptoClaveVideo concepto = conceptoRepositorio.findById(idConcepto);
        if (concepto == null) throw new NotFoundException("Concepto no encontrado");
        if (solicitud == null) throw new IllegalArgumentException("Datos vacíos");

        if (solicitud.nombre() != null && !solicitud.nombre().isBlank()) {
            concepto.nombre = solicitud.nombre().strip();
        }
        if (solicitud.definicion() != null) concepto.definicion = solicitud.definicion().strip();
        if (solicitud.tiempoInicio() != null) concepto.tiempoInicio = solicitud.tiempoInicio();
        if (solicitud.tiempoFin() != null) concepto.tiempoFin = solicitud.tiempoFin();
        return aDto(concepto);
    }

    @Transactional
    public void eliminarConcepto(Long idConcepto) {
        ConceptoClaveVideo concepto = conceptoRepositorio.findById(idConcepto);
        if (concepto == null) throw new NotFoundException("Concepto no encontrado");
        conceptoRepositorio.delete(concepto);
    }

    // ── Auxiliares ──────────────────────────────────────────────────────────

    private int siguienteOrdenCapitulo(Long idVideo) {
        Integer max = capituloRepositorio.getEntityManager()
            .createQuery("SELECT MAX(c.ordenCapitulo) FROM CapituloVideo c WHERE c.video.id = :id", Integer.class)
            .setParameter("id", idVideo)
            .getSingleResult();
        return max == null ? 0 : max + 1;
    }

    private int siguienteOrdenConcepto(Long idVideo) {
        Integer max = conceptoRepositorio.getEntityManager()
            .createQuery("SELECT MAX(c.ordenConcepto) FROM ConceptoClaveVideo c WHERE c.video.id = :id", Integer.class)
            .setParameter("id", idVideo)
            .getSingleResult();
        return max == null ? 0 : max + 1;
    }

    private CapituloVideoDTO aDto(CapituloVideo c) {
        return new CapituloVideoDTO(c.id, c.titulo, c.descripcion, c.tiempoInicio, c.tiempoFin,
            c.ordenCapitulo, c.origen, c.creadoManual, c.generadoPorIa);
    }

    private ConceptoClaveVideoDTO aDto(ConceptoClaveVideo c) {
        return new ConceptoClaveVideoDTO(c.id, c.nombre, c.definicion, c.tiempoInicio, c.tiempoFin,
            c.ordenConcepto, c.creadoManual, c.generadoPorIa);
    }
}
