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

    // Resumen de la clase persistido (se rellena al analizar o editar manualmente)
    @Column(columnDefinition = "TEXT")
    public String resumen;

    @Column
    public String asignatura;

    // Nombre del profesor en texto (compatibilidad con datos antiguos)
    @Column
    public String profesor;

    // Profesor concreto de esta clase como relación (opcional)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "profesor_id")
    public Profesor profesorObj;

    @Column(name = "fecha_clase")
    public LocalDate fechaClase;

    @Column
    public Boolean completado = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "asignatura_id")
    public Asignatura asignaturaObj;
}
