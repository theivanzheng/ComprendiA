package es.comprendia.repositorio;

import es.comprendia.entidad.NotaConceptoAsignatura;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class NotaConceptoAsignaturaRepositorio implements PanacheRepository<NotaConceptoAsignatura> {

    // Busca la nota por asignatura y nombre de concepto (insensible a mayúsculas)
    public Optional<NotaConceptoAsignatura> buscar(Long asignaturaId, String nombreConcepto) {
        return find("asignaturaId = ?1 AND LOWER(nombreConcepto) = LOWER(?2)", asignaturaId, nombreConcepto)
            .firstResultOptional();
    }
}
