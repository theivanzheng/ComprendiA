package es.comprendia.entidad;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Nota personal global para un concepto dentro de una asignatura.
 * No depende de usuarios ni de una entidad global de concepto: se identifica por
 * (asignaturaId + nombreConcepto). Una sola nota por concepto y asignatura.
 */
@Entity
@Table(name = "notas_concepto_asignatura")
public class NotaConceptoAsignatura extends PanacheEntity {

    @Column(name = "asignatura_id", nullable = false)
    public Long asignaturaId;

    @Column(name = "nombre_concepto", nullable = false)
    public String nombreConcepto;

    @Column(columnDefinition = "TEXT")
    public String nota;

    @Column(name = "fecha_actualizacion")
    public LocalDateTime fechaActualizacion;
}
