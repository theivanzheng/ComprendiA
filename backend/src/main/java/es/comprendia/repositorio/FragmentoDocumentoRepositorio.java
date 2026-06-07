package es.comprendia.repositorio;

import es.comprendia.dto.FuenteDocumentoDTO;
import es.comprendia.entidad.FragmentoDocumento;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class FragmentoDocumentoRepositorio implements PanacheRepository<FragmentoDocumento> {

    private static final Logger LOG = Logger.getLogger(FragmentoDocumentoRepositorio.class);

    /**
     * Búsqueda semántica en los documentos de una clase: distancia coseno con pgvector, devolviendo
     * los trozos más parecidos junto al nombre del documento de origen.
     */
    @SuppressWarnings("unchecked")
    public List<FuenteDocumentoDTO> buscarPorSimilitud(Long idVideo, String embeddingPregunta, int limite) {
        List<Object[]> filas = getEntityManager()
            .createNativeQuery(
                "SELECT fd.texto, dc.nombre_archivo, " +
                "       1 - (fd.embedding_json <=> CAST(:emb AS vector)) AS similitud " +
                "FROM fragmentos_documento fd " +
                "JOIN documentos_clase dc ON dc.id = fd.documento_id " +
                "WHERE fd.video_id = :videoId AND fd.embedding_json IS NOT NULL " +
                "ORDER BY fd.embedding_json <=> CAST(:emb AS vector) " +
                "LIMIT :limite")
            .setParameter("emb", embeddingPregunta)
            .setParameter("videoId", idVideo)
            .setParameter("limite", limite)
            .getResultList();

        return filas.stream()
            .map(fila -> new FuenteDocumentoDTO(
                (String) fila[0],
                (String) fila[1],
                ((Number) fila[2]).doubleValue()))
            .toList();
    }

    /** Guarda el embedding (como vector pgvector) de un fragmento ya persistido. */
    @Transactional
    public void actualizarEmbedding(Long idFragmento, String embeddingJson) {
        int filas = getEntityManager()
            .createNativeQuery(
                "UPDATE fragmentos_documento SET embedding_json = CAST(:ej AS vector) WHERE id = :id")
            .setParameter("ej", embeddingJson)
            .setParameter("id", idFragmento)
            .executeUpdate();
        if (filas == 0) {
            LOG.errorf("[Documento] Fragmento id=%s no encontrado — embedding no guardado", idFragmento);
        }
    }

    /** Borra todos los fragmentos de un documento. Devuelve cuántos. */
    @Transactional
    public long eliminarPorDocumento(Long idDocumento) {
        return delete("documento.id", idDocumento);
    }
}
