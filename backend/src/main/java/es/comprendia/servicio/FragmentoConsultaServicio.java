package es.comprendia.servicio;

import es.comprendia.dto.FragmentoDTO;
import es.comprendia.repositorio.FragmentoTranscripcionRepositorio;
import es.comprendia.repositorio.VideoRepositorio;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

@ApplicationScoped
public class FragmentoConsultaServicio {

    @Inject
    VideoRepositorio videoRepositorio;

    @Inject
    FragmentoTranscripcionRepositorio fragmentoRepositorio;

    @Transactional
    public List<FragmentoDTO> obtenerPorVideoId(Long id) {
        if (videoRepositorio.findById(id) == null) {
            throw new NotFoundException("Vídeo con id " + id + " no encontrado");
        }
        return fragmentoRepositorio.buscarPorVideoOrdenado(id).stream()
            .map(f -> new FragmentoDTO(f.texto, f.tiempoInicio, f.tiempoFin, f.ordenFragmento))
            .toList();
    }
}
