package es.comprendia.entidad;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Un documento del curso (PDF, Word, etc.) subido y asociado a una clase/vídeo. Guarda solo los
 * metadatos del archivo; el contenido troceado y vectorizado vive en {@link FragmentoDocumento}.
 */
@Entity
@Table(name = "documentos_clase")
public class DocumentoClase extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false)
    public Video video;

    @Column(name = "nombre_archivo", nullable = false)
    public String nombreArchivo;

    @Column(name = "tipo_mime")
    public String tipoMime;

    @Column(name = "num_fragmentos")
    public Integer numFragmentos;

    @Column(name = "fecha_subida", nullable = false)
    public LocalDateTime fechaSubida;
}
