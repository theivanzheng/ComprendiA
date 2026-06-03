package es.comprendia.dto;

import java.util.List;

public record RespuestaRagDTO(
    String respuesta,
    List<ResultadoBusquedaDTO> fuentes
) {}
