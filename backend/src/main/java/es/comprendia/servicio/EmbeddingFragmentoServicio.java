package es.comprendia.servicio;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.comprendia.entidad.FragmentoTranscripcion;
import es.comprendia.repositorio.FragmentoTranscripcionRepositorio;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class EmbeddingFragmentoServicio {

    private static final Logger LOG = Logger.getLogger(EmbeddingFragmentoServicio.class);

    @Inject
    EmbeddingServicio embeddingServicio;

    @Inject
    FragmentoTranscripcionRepositorio fragmentoRepositorio;

    @ConfigProperty(name = "comprendia.embedding.habilitado", defaultValue = "true")
    boolean embeddingHabilitado;

    private final ObjectMapper mapeadorJson = new ObjectMapper();

    public void generarYGuardar(List<FragmentoTranscripcion> fragmentos) {
        if (!embeddingHabilitado) {
            LOG.info("[Embedding] Generación de embeddings deshabilitada (comprendia.embedding.habilitado=false)");
            return;
        }
        LOG.infof("[Embedding] ===== Iniciando generación de embeddings para %d fragmentos =====",
            fragmentos.size());

        if (fragmentos.isEmpty()) {
            LOG.warn("[Embedding] Lista de fragmentos vacía — no hay nada que procesar");
            return;
        }

        long inicioTotal = System.currentTimeMillis();
        int exitos = 0;
        for (FragmentoTranscripcion fragmento : fragmentos) {
            LOG.infof("[Embedding] Procesando fragmento id=%s, texto=%s chars",
                fragmento.id,
                fragmento.texto != null ? fragmento.texto.length() : "NULL");

            if (fragmento.texto == null || fragmento.texto.isBlank()) {
                LOG.warnf("[Embedding] Fragmento id=%s tiene texto vacío — se omite", fragmento.id);
                continue;
            }

            try {
                long inicioEmbedding = System.currentTimeMillis();
                List<Double> embedding = embeddingServicio.generarEmbedding(fragmento.texto);
                LOG.infof("[Tiempo] Embedding fragmento id=%s — %d dimensiones en %d ms",
                    fragmento.id, embedding.size(), System.currentTimeMillis() - inicioEmbedding);

                String embeddingJson = mapeadorJson.writeValueAsString(embedding);
                fragmentoRepositorio.actualizarEmbedding(fragmento.id, embeddingJson);
                exitos++;

            } catch (Exception e) {
                LOG.errorf(e, "[Embedding] FALLO en fragmento id=%s: %s", fragmento.id, e.getMessage());
            }
        }

        LOG.infof("[Tiempo] Embeddings completados en %d ms — %d/%d fragmentos",
            System.currentTimeMillis() - inicioTotal, exitos, fragmentos.size());
    }
}
