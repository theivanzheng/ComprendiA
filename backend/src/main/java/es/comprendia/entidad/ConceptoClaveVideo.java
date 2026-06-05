package es.comprendia.entidad;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "conceptos_clave_video")
public class ConceptoClaveVideo extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false)
    public Video video;

    @Column(nullable = false)
    public String nombre;

    @Column(columnDefinition = "TEXT")
    public String definicion;

    @Column(name = "tiempo_inicio")
    public Double tiempoInicio;

    @Column(name = "tiempo_fin")
    public Double tiempoFin;

    @Column(name = "orden_concepto")
    public Integer ordenConcepto;

    @Column(name = "creado_manual")
    public Boolean creadoManual = false;

    @Column(name = "generado_por_ia")
    public Boolean generadoPorIa = false;
}
