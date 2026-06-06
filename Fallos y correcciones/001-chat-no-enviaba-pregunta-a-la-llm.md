# 001 — El chat conversacional no enviaba la pregunta a la LLM

- **Fecha:** 2026-06-05
- **Componente:** backend / chat (RAG)
- **Archivos:** `backend/src/main/java/es/comprendia/servicio/ChatGptServicio.java`, función `construirCuerpoConversacion(...)`
- **Commit del arreglo:** `f3d0679`

## Síntoma

El chat del asistente respondía mal de forma sistemática:

- A la **primera** pregunta contestaba con un saludo/evasiva: *"Claro, ¿sobre qué parte del vídeo te gustaría hablar?"*.
- En las preguntas siguientes daba la sensación de ir **"una pregunta por detrás"**: respondía al tema del turno anterior. Ejemplo real: tras preguntar por una URL pegada, a la siguiente pregunta sobre ropa contestaba *"no puedo acceder a enlaces externos"*.
- No encontraba información que **sí estaba** en la transcripción (p. ej. el color de unos pantalones que aparecía en el minuto 2:17).

## Diagnóstico

Se usó **depuración diferencial** comparando dos rutas que comparten el mismo emisor HTTP a OpenAI pero distinto ensamblador del mensaje:

- Ruta sana: `GET /api/transcripciones/{id}/responder` → `RagServicio.responderLocal` → `ChatGptServicio.completar` → `construirCuerpo`.
- Ruta defectuosa: `POST /api/transcripciones/{id}/conversar` → `RagServicio.responderConversacion` → `ChatGptServicio.completarConversacion` → `construirCuerpoConversacion`.

Pasos:

1. Se probaron ambos *endpoints* con `curl` (caja negra) y la misma pregunta. `/responder` acertaba ("blanco, 2:17"); `/conversar` evadía.
2. Se igualaron *prompt*, formato de mensaje y contexto entre las dos rutas, y aun así divergían.
3. Se descartó el **no determinismo** del modelo repitiendo cada llamada **3 veces**: `/responder` 3/3 correcto, `/conversar` 3/3 evasivo → el fallo era **sistemático**, no aleatorio.
4. La **inspección del código** de `construirCuerpoConversacion` reveló la causa.

## Causa raíz

La función construía el mensaje del usuario (los extractos recuperados por RAG + la pregunta actual) en un `StringBuilder usuario`, pero **nunca lo añadía a la lista `mensajes`** que se serializa al array `messages` de la API de OpenAI.

A la LLM solo le llegaban el mensaje `system` (las instrucciones) y el `historial`, **pero no la pregunta actual ni los fragmentos**. Esto producía un **desfase de un turno** en el estado de la conversación:

- Sin historial → la lista era `[system]` → el modelo solo veía las instrucciones → saludaba.
- Con historial → la lista era `[system, ...turnos previos...]` → el modelo respondía al **último turno del historial**, es decir, a la pregunta **anterior**.

El cómputo del mensaje del usuario quedaba como **valor no utilizado** (*dead computation*).

## Corrección

Una sola sentencia en `construirCuerpoConversacion`, justo después de montar el texto del usuario:

```java
// Sin esta línea, a la LLM solo le llegaban el system y el historial, no la pregunta actual.
mensajes.add(Map.of("role", "user", "content", usuario.toString()));
```

`Map.of("role","user","content", ...)` crea un mensaje (quién habla + qué dice) y `mensajes.add(...)` lo incorpora a la lista que recibe la LLM.

Como **ambas** rutas conversacionales delegan en este mismo ensamblador, el arreglo cubre con un único cambio:

- el chat por **HTTP** (`completarConversacion`, usado por `TranscripcionConsultaRecurso.conversar`), y
- el chat por **WebSocket en streaming** (`completarConversacionStream`, usado por `ChatWebSocket`).

## Verificación

- Pruebas funcionales con `curl` contra `/conversar`:
  - *"¿de qué color le gusta llevar los pantalones chinos?"* → **blanco (2:17)**.
  - Referencia implícita *"¿y de qué colores los de lino?"* → **blanco, negro, beige/marrón (1:21)** (mantiene el hilo).
- Suite de regresión: `mvn test` → **19/19 en verde**.

## Concepto aprendido

- La API de chat de un LLM espera una **lista de mensajes** con roles (`system` / `user` / `assistant`); si falta el turno `user`, el modelo responde a lo que tenga (el `system` o el historial), de ahí el desfase.
- Un **valor calculado pero no usado** es una señal de alarma: aquí el `StringBuilder` se llenaba y se descartaba.
- La **depuración diferencial** (comparar una ruta que funciona con otra que no, aislando variables y descartando el azar con repeticiones) es muy eficaz para localizar diferencias estructurales.
