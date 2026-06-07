package es.comprendia.entidad;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

/**
 * Fragmento (trozo) de un documento del curso, con su embedding para la búsqueda semántica.
 * Equivale a {@link FragmentoTranscripcion} pero para documentos en vez de para el vídeo: no tiene
 * marcas de tiempo. Se guarda el {@code video} además del {@code documento} para poder buscar por
 * clase de forma directa (mismo patrón que la transcripción).
 */
@Entity
@Table(name = "fragmentos_documento")
public class FragmentoDocumento extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id", nullable = false)
    public DocumentoClase documento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false)
    public Video video;

    @Column(columnDefinition = "TEXT", nullable = false)
    public String texto;

    @Column(name = "orden_fragmento")
    public Integer ordenFragmento;

    // Tipo pgvector en PostgreSQL; en tests H2 lo mapea a un dominio VARCHAR.
    @Column(name = "embedding_json", columnDefinition = "vector", nullable = true,
            insertable = false, updatable = false)
    public String embeddingJson;
}
