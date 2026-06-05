package es.comprendia.entidad;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "asignaturas")
public class Asignatura extends PanacheEntity {

    @Column(nullable = false)
    public String nombre;

    @Column(columnDefinition = "TEXT")
    public String descripcion;

    // Nombre del profesor principal en texto (compatibilidad con datos antiguos)
    @Column
    public String profesor;

    // Profesor principal como relación (fuente de verdad)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "profesor_id")
    public Profesor profesorObj;

    @Column(name = "fecha_creacion", nullable = false)
    public LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    public LocalDateTime fechaActualizacion;
}
