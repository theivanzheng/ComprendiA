package es.comprendia.entidad;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "profesores")
public class Profesor extends PanacheEntity {

    @Column(nullable = false)
    public String nombre;

    @Column
    public String email;

    @Column(name = "fecha_creacion", nullable = false)
    public LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    public LocalDateTime fechaActualizacion;
}
