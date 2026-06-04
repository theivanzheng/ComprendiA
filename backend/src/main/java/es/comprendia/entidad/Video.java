package es.comprendia.entidad;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "videos")
public class Video extends PanacheEntity {

    @Column(name = "youtube_id", nullable = false)
    public String youtubeId;

    @Column(nullable = false)
    public String titulo;

    @Column(name = "fuente_transcripcion")
    public String fuenteTranscripcion;

    @Column(name = "fecha_creacion", nullable = false)
    public LocalDateTime fechaCreacion;

    @Column
    public String asignatura;

    @Column
    public String profesor;

    @Column(name = "fecha_clase")
    public LocalDate fechaClase;

    @Column
    public Boolean completado = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "asignatura_id")
    public Asignatura asignaturaObj;
}
