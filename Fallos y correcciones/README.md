# Fallos y correcciones

Registro de incidencias encontradas durante el desarrollo de ComprendiA y cómo se resolvieron.
Sirve como bitácora técnica y como material para el capítulo de **Pruebas / Desarrollo** de la
memoria del TFG (apartado de incidencias y resolución).

Cada caso es un archivo Markdown numerado (`NNN-titulo-corto.md`) que sigue la plantilla de abajo.

## Índice

| Nº | Caso | Estado |
|----|------|--------|
| 001 | [El chat conversacional no enviaba la pregunta a la LLM](001-chat-no-enviaba-pregunta-a-la-llm.md) | Resuelto |
| 002 | [Contaminación de la "entidad reciente" en el chat](002-contaminacion-de-la-entidad-reciente.md) | Resuelto |
| 003 | [Fragmentos demasiado pequeños (mala recuperación)](003-fragmentos-demasiado-pequenos.md) | Resuelto |
| 004 | [La recuperación top-k se saltaba información presente](004-rag-hibrido-contexto-completo.md) | Resuelto |
| 005 | [El pill de asignatura no mostraba "(sugerencia)"](005-pill-sugerencia-no-se-mostraba.md) | Resuelto |

## Plantilla para nuevos casos

```markdown
# NNN — Título corto del problema

- **Fecha:** AAAA-MM-DD
- **Componente:** (backend / frontend / pipeline / chat / ...)
- **Archivos:** rutas y funciones implicadas

## Síntoma
Qué se observaba (con ejemplos reales si los hay).

## Diagnóstico
Cómo se localizó el problema (pruebas, logs, comparaciones...).

## Causa raíz
La causa real, explicada con claridad.

## Corrección
El cambio aplicado (con el fragmento de código si procede).

## Verificación
Cómo se comprobó que quedaba arreglado.

## Concepto aprendido
La idea de ingeniería que conviene recordar.
```
