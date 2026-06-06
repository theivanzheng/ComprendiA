# 004 — La recuperación top-k se saltaba información presente

- **Fecha:** 2026-06-05
- **Componente:** backend / chat (RAG)
- **Archivos:** `backend/.../servicio/RagServicio.java` (`construirContextoParaVideo`)
- **Commit del arreglo:** `1204eba`

## Síntoma

El asistente decía *"no se menciona"* algo que **sí estaba** en el vídeo. Caso real: la pregunta por
el color de unos pantalones, cuando la transcripción decía claramente *"white chino"* en el minuto
2:18.

## Diagnóstico

Se inspeccionaron los 18 fragmentos del vídeo a través del endpoint `GET /api/transcripciones/{id}/fragmentos`
(caja negra, sin tocar la base de datos a mano). El dato estaba en el fragmento de 2:18. Pero la
recuperación devolvía solo los **8 más parecidos** (`NUM_FRAGMENTOS = 8`), y ese fragmento no
siempre entraba → al modelo no le llegaba el dato.

## Causa raíz

**Problema de *recall*** de la recuperación top-k: en un vídeo corto, los 8 fragmentos más parecidos
pueden no incluir el fragmento relevante.

## Corrección

`construirContextoParaVideo(videoId, fuentes)`: si el vídeo es pequeño
(`<= UMBRAL_CONTEXTO_COMPLETO = 30` fragmentos), se le pasa a la LLM la **transcripción completa**
(cabe de sobra en el contexto) en lugar de solo los más parecidos. Si es grande, se mantiene el RAG
clásico (top-k). Las "fuentes" que se muestran al usuario siguen siendo las más relevantes. Se añadió
además un log de diagnóstico con los momentos recuperados (`[RAG-WS] ... momentos=[...]`).

## Verificación

`[RAG] Contexto COMPLETO: 18 fragmentos (vídeo pequeño id=851)` en los logs, y el dato del minuto
2:18 ya disponible para el modelo.

> Nota: esta mejora era necesaria, pero el fallo de fondo por el que el chat seguía evadiéndose era
> el del caso [001](001-chat-no-enviaba-pregunta-a-la-llm.md) (no se enviaba la pregunta a la LLM).

## Concepto aprendido

RAG es la solución cuando "todo" no cabe; pero cuando el contenido es pequeño, **pasar el contexto
completo** maximiza el *recall* y evita perder información por una recuperación incompleta. Un
**híbrido** (completo si cabe, top-k si no) combina lo mejor de ambos enfoques.
