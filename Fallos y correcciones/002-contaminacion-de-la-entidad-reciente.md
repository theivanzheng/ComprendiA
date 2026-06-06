# 002 — Contaminación de la "entidad reciente" en el chat

- **Fecha:** 2026-06-05
- **Componente:** backend / chat (RAG) + frontend
- **Archivos:** `backend/.../servicio/RagServicio.java`, `backend/.../websocket/ChatWebSocket.java`, `frontend/src/app/app.ts` (`extraerEntidad`)
- **Commit del arreglo:** `5949b48`

> Nota: este caso era un problema real de *calidad de la recuperación*, pero el síntoma más
> llamativo ("ir una pregunta por detrás") resultó tener una causa más profunda, documentada en
> el caso [001](001-chat-no-enviaba-pregunta-a-la-llm.md). Aun así, la mejora de este caso es válida.

## Síntoma

Al preguntar por un tema nuevo, el asistente arrastraba el tema anterior. Ejemplo: tras hablar
del *iPhone*, al preguntar *"¿de qué marcas de zapatillas habla?"* respondía sobre el iPhone. En
los logs aparecía una entidad mal extraída: `entidad='Loro Piana Si'`.

## Diagnóstico

Los logs (`[RAG-WS] ... entidad='...'`) mostraban que la "entidad reciente" se inyectaba **en todas
las preguntas**, tanto en la búsqueda semántica como en el *prompt*, incluso cuando la pregunta
introducía un sujeto nuevo. Además, la extracción de la entidad en el frontend recogía basura
(*"Loro Piana Si"*, cogiendo el "Si" de "Si tienes…").

## Causa raíz

Dos defectos:

1. **Sobre-inyección de la entidad:** `RagServicio` añadía `entidadReciente` a la consulta y al
   *prompt* siempre, sin distinguir si la pregunta era una **referencia implícita** (un pronombre)
   o traía sujeto propio.
2. **Extracción ruidosa:** `extraerEntidad` (frontend) cruzaba los límites de frase (eliminaba los
   puntos antes de partir) y no descartaba conectores con mayúscula (Si, No, El…).

## Corrección

- **Backend:** se añadieron `esReferenciaImplicita(pregunta)` y `entidadAplicable(pregunta, entidad)`
  en `RagServicio`: la entidad solo se usa si la pregunta es realmente una referencia implícita
  (pronombre o pregunta muy corta); si trae sujeto nuevo, se ignora. Se aplica igual en la búsqueda
  y en el *prompt*, tanto en la ruta WebSocket (`prepararConversacion`) como en la HTTP
  (`responderConversacion`). `PreparacionRag` pasó a exponer la *entidad efectiva*.
- **Frontend:** `extraerEntidad` ahora respeta los límites de frase (no cruza puntos) y descarta
  conectores/afirmaciones con mayúscula, evitando entidades como *"Loro Piana Si"*.

## Verificación

Pruebas manuales: pregunta con sujeto nuevo → `entidad aplicada='null'` en los logs; referencia
implícita ("¿y el móvil?") → mantiene la entidad correcta.

## Concepto aprendido

Una heurística de "memoria de contexto" debe aplicarse **solo cuando aporta** (referencias
implícitas) y nunca de forma indiscriminada, o contamina las respuestas. Y al extraer entidades de
texto hay que respetar los **límites de frase** y filtrar **palabras vacías**.
