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

    @Column(name = "embedding_json", columnDefinition = "TEXT", nullable = true)
    public String embeddingJson;
}
