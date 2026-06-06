# 005 — El pill de asignatura no mostraba "(sugerencia)"

- **Fecha:** 2026-06-05
- **Componente:** backend + frontend (autoasignación de asignatura)
- **Archivos:** `backend/.../servicio/VideoConsultaServicio.java`, `backend/.../servicio/ClasificacionAsignaturaServicio.java`, `frontend/src/app/app.html`, `frontend/src/app/app.css`

## Síntoma

Al procesar un vídeo de un canal ya asociado a una asignatura, el sistema rellenaba la asignatura
(y el profesor) correctamente, pero en la interfaz **no aparecía la marca "(sugerencia)"** que
indica que la asignación fue automática y editable.

## Diagnóstico

Se revisó toda la cadena: el endpoint de detalle de clase usa `VideoConsultaServicio.convertirAResumen`,
que rellena el campo `asignaturaSugerida` del DTO. El frontend (`seleccionarVideo`) lo lee bien. El
problema estaba en cómo se calculaba el booleano en el backend.

## Causa raíz

Dos cosas:

1. **Condición que enmascaraba el flag:** `convertirAResumen` devolvía `asignaturaSugerida` solo si
   además `video.asignaturaObj != null`. Ese acceso a una relación *lazy* podía hacer que el flag
   real quedara oculto.
2. **Texto incorrecto:** la etiqueta decía "(sugerida)" en vez del texto pedido "(sugerencia)".

## Corrección

- **Backend:** el DTO ahora se basa únicamente en `Boolean.TRUE.equals(video.asignaturaSugerida)`
  (el flag es la fuente de verdad; lo pone el clasificador y lo limpia la asignación manual). Además,
  al asignar por canal/semántica, `ClasificacionAsignaturaServicio` también **sugiere el profesor**
  de la asignatura si el vídeo no tenía uno.
- **Frontend:** el pill muestra "(sugerencia)" de forma discreta (`app.html` + `app.css`).

## Verificación

Procesar un vídeo nuevo de un canal asociado → el pill muestra "Asignatura: X (sugerencia)"; al
cambiar la asignatura a mano, la marca desaparece (queda como `MANUAL`).

## Concepto aprendido

Un **flag de estado** debe ser la única fuente de verdad y no condicionarse a otros campos que
puedan enmascararlo (sobre todo con relaciones *lazy*). Y conviene separar el **estado** (metadato:
"es sugerencia") de la **presentación** (el texto "(sugerencia)"): el nombre real de la asignatura
nunca contiene esa palabra.
