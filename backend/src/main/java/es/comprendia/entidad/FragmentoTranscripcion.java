package es.comprendia.entidad;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "fragmentos_transcripcion")
public class FragmentoTranscripcion extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false)
    public Video video;

    @Column(columnDefinition = "TEXT", nullable = false)
    public String texto;

    @Column(name = "tiempo_inicio")
    public Double tiempoInicio;

    @Column(name = "tiempo_fin")
    public Double tiempoFin;

    @Column(name = "orden_fragmento")
    public Integer ordenFragmento;

    // Tipo vector(1536) en PostgreSQL; se lee como String via JDBC y se escribe via SQL nativo
    @Column(name = "embedding_json", columnDefinition = "vector(1536)", nullable = true,
            insertable = false, updatable = false)
    public String embeddingJson;
}
