package es.comprendia.servicio;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.comprendia.dto.DocumentoClaseDTO;
import es.comprendia.entidad.DocumentoClase;
import es.comprendia.entidad.FragmentoDocumento;
import es.comprendia.entidad.Video;
import es.comprendia.repositorio.DocumentoClaseRepositorio;
import es.comprendia.repositorio.FragmentoDocumentoRepositorio;
import es.comprendia.repositorio.VideoRepositorio;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Orquesta la gestión de documentos del curso asociados a una clase/vídeo: subida (extracción de
 * texto + troceado + embeddings), listado y borrado. La vectorización reutiliza el mismo modelo de
 * embeddings y la misma sintaxis pgvector que la transcripción.
 */
@ApplicationScoped
public class DocumentoServicio {

    private static final Logger LOG = Logger.getLogger(DocumentoServicio.class);

    @Inject VideoRepositorio videoRepositorio;
    @Inject DocumentoClaseRepositorio documentoRepositorio;
    @Inject FragmentoDocumentoRepositorio fragmentoDocumentoRepositorio;
    @Inject ExtraccionDocumentoServicio extraccionDocumentoServicio;
    @Inject EmbeddingServicio embeddingServicio;

    @ConfigProperty(name = "comprendia.embedding.habilitado", defaultValue = "true")
    boolean embeddingHabilitado;

    private final ObjectMapper mapeadorJson = new ObjectMapper();

    @Transactional
    public DocumentoClaseDTO subir(Long idVideo, String nombreArchivo, String tipoMime, byte[] contenido) {
        Video video = videoRepositorio.findById(idVideo);
        if (video == null) {
            throw new NotFoundException("Vídeo con id " + idVideo + " no encontrado");
        }

        List<String> trozos = extraccionDocumentoServicio.extraerYTrocear(contenido, nombreArchivo);
        if (trozos.isEmpty()) {
            throw new BadRequestException(
                "No se pudo extraer texto del documento. Si es un PDF escaneado haría falta OCR (aún no soportado).");
        }

        DocumentoClase documento = new DocumentoClase();
        documento.video = video;
        documento.nombreArchivo = nombreArchivo;
        documento.tipoMime = tipoMime;
        documento.fechaSubida = LocalDateTime.now();
        documento.numFragmentos = trozos.size();
        documentoRepositorio.persist(documento);

        List<FragmentoDocumento> fragmentos = new ArrayList<>(trozos.size());
        for (int i = 0; i < trozos.size(); i++) {
            FragmentoDocumento fragmento = new FragmentoDocumento();
            fragmento.documento = documento;
            fragmento.video = video;
            fragmento.texto = trozos.get(i);
            fragmento.ordenFragmento = i;
            fragmentoDocumentoRepositorio.persist(fragmento);
            fragmentos.add(fragmento);
        }
        fragmentoDocumentoRepositorio.getEntityManager().flush(); // asegura ids antes del UPDATE del embedding

        if (embeddingHabilitado) {
            int exitos = 0;
            for (FragmentoDocumento fragmento : fragmentos) {
                try {
                    List<Double> embedding = embeddingServicio.generarEmbedding(fragmento.texto);
                    fragmentoDocumentoRepositorio.actualizarEmbedding(
                        fragmento.id, mapeadorJson.writeValueAsString(embedding));
                    exitos++;
                } catch (Exception e) {
                    LOG.errorf(e, "[Documento] Fallo al vectorizar fragmento id=%s", fragmento.id);
                }
            }
            LOG.infof("[Documento] '%s' subido a vídeo id=%s: %d/%d fragmentos vectorizados",
                nombreArchivo, idVideo, exitos, fragmentos.size());
        } else {
            LOG.infof("[Documento] '%s' guardado sin vectorizar (embeddings deshabilitados)", nombreArchivo);
        }

        return aDTO(documento);
    }

    public List<DocumentoClaseDTO> listar(Long idVideo) {
        return documentoRepositorio.listarPorVideo(idVideo).stream().map(this::aDTO).toList();
    }

    @Transactional
    public void eliminar(Long idDocumento) {
        DocumentoClase documento = documentoRepositorio.findById(idDocumento);
        if (documento == null) {
            throw new NotFoundException("Documento con id " + idDocumento + " no encontrado");
        }
        fragmentoDocumentoRepositorio.eliminarPorDocumento(idDocumento);
        documentoRepositorio.delete(documento);
        LOG.infof("[Documento] Eliminado documento id=%s ('%s')", idDocumento, documento.nombreArchivo);
    }

    private DocumentoClaseDTO aDTO(DocumentoClase documento) {
        return new DocumentoClaseDTO(
            documento.id, documento.nombreArchivo, documento.tipoMime,
            documento.numFragmentos, documento.fechaSubida);
    }
}
