package es.comprendia.entidad;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "capitulos_video")
public class CapituloVideo extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false)
    public Video video;

    @Column(nullable = false)
    public String titulo;

    @Column(columnDefinition = "TEXT")
    public String descripcion;

    @Column(name = "tiempo_inicio")
    public Double tiempoInicio;

    @Column(name = "tiempo_fin")
    public Double tiempoFin;

    @Column(name = "orden_capitulo")
    public Integer ordenCapitulo;

    // Origen textual histórico: "IA", "AUTO" o "MANUAL"
    @Column(nullable = false)
    public String origen;

    @Column(name = "creado_manual")
    public Boolean creadoManual = false;

    @Column(name = "generado_por_ia")
    public Boolean generadoPorIa = false;
}
