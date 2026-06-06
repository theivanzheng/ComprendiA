# Migración a LangChain4j

Bitácora de la integración de **LangChain4j** (vía la extensión `quarkus-langchain4j`) para
alinear el proyecto con lo descrito en la memoria técnica del TALENT. Se documenta paso a paso.

- **Rama:** `feat/langchain4j`
- **Punto de partida:** `feat/websockets-tiempo-real` (chat por WebSocket + streaming + RAG funcionando con llamadas HTTP directas a OpenAI).

## Por qué

La memoria del TALENT propone **Quarkus + LangChain4j**. Hasta ahora la capa de IA
(`EmbeddingServicio`, `ChatGptServicio`) llamaba a la API de OpenAI con el cliente HTTP de Java a
mano. LangChain4j aporta:

- Menos código de fontanería (HTTP, JSON, *streaming* SSE ya resueltos).
- Abstracción de modelo: cambiar OpenAI ↔ Gemini/Claude/Ollama por configuración.
- Componentes de RAG (almacenes de vectores, *retrievers*) y base para herramientas/MCP.

## Estrategia

Migración **por capas**, manteniendo el comportamiento y la API interna (mismas firmas de
método) para no romper el resto del sistema. Tras cada capa: compilar + tests en verde.

1. **Embeddings** — `EmbeddingServicio` usa el `EmbeddingModel` de LangChain4j.
2. **Chat** — `ChatGptServicio` usa `ChatLanguageModel` / `StreamingChatLanguageModel`.
3. **RAG** (opcional) — almacén de vectores / *retriever* de LangChain4j sobre pgvector.

## Registro de cambios

### Paso 0 — Preparación
- Creada la rama `feat/langchain4j` y este documento.

### Paso 1 — Embeddings con LangChain4j ✅
- **Dependencia:** `io.quarkiverse.langchain4j:quarkus-langchain4j-openai` versión `0.25.0`
  (compatible con Quarkus 3.17). Añadida en `pom.xml`.
- **Configuración** (`application.properties`):
  ```
  quarkus.langchain4j.openai.api-key=${OPENAI_API_KEY:}
  quarkus.langchain4j.openai.embedding-model.model-name=text-embedding-3-small
  quarkus.langchain4j.openai.timeout=60s
  ```
  En tests (`src/test/resources/application.properties`) se pone una clave ficticia (no se llama
  a OpenAI en pruebas).
- **Código:** `EmbeddingServicio` ya no monta la petición HTTP a mano. Ahora inyecta el
  `dev.langchain4j.model.embedding.EmbeddingModel` y hace `embeddingModel.embed(texto).content().vector()`.
  Se conserva la **firma pública** `List<Double> generarEmbedding(String)`, así que `RagServicio`,
  `EmbeddingFragmentoServicio` y `ClasificacionAsignaturaServicio` no cambian.
- **Resultado:** compila y los 19 tests pasan (Quarkus arranca con la extensión).
- **Pendiente de validar en vivo:** generar embeddings reales requiere una `OPENAI_API_KEY` válida
  (la anterior daba 401).

### Paso 2 — Chat con LangChain4j ✅
- **`ChatGptServicio`** ya no monta peticiones HTTP ni parsea SSE a mano. Usa los modelos de
  LangChain4j:
  - `ChatLanguageModel` (síncrono) para `completar`, `completarConversacion`,
    `completarPersonalizado` y `completarEstructurado`.
  - `StreamingChatLanguageModel` para `completarConversacionStream` (el chat por WebSocket): el
    `StreamingResponseHandler` entrega cada token por `onChunk` y un `CompletableFuture` espera al
    `onComplete` para devolver el texto completo. Adiós al parseo manual de Server-Sent Events.
- **Parámetros por método:** como cada llamada usa distinta temperatura / max tokens / modo JSON,
  los modelos se **construyen y cachean** por combinación de parámetros (`OpenAiChatModel.builder()`
  / `OpenAiStreamingChatModel.builder()`), conservando exactamente los valores de antes
  (chat 0.3/500, análisis 0.2/`json_object`, etc.).
- **Mensajes:** se construyen con los tipos de LangChain4j (`SystemMessage`, `UserMessage`,
  `AiMessage` para el historial) en vez de `Map`/JSON.
- **Firmas públicas intactas:** `RagServicio`, `AnalisisClaseServicio` y `ChatWebSocket` no cambian.
- **Resultado:** compila y los 19 tests pasan.

### Paso 3 — Multi-modelo (OpenAI ↔ Google Gemini) ✅

Aprovechando que LangChain4j abstrae el modelo, el **chat** del asistente ahora puede
cambiarse entre proveedores desde un selector en el frontend. Decisiones clave:

- **Solo afecta al chat/generación, NO a los embeddings.** Los vectores almacenados viven en el
  espacio de `text-embedding-3-small` (OpenAI); cambiar de modelo de embeddings invalidaría la
  recuperación (RAG) y obligaría a reprocesar todos los vídeos. Por eso los embeddings siguen fijos.
- **Proveedores:** OpenAI (`gpt-4o-mini`, por defecto) y Google Gemini (`gemini-1.5-flash`).
- **El análisis estructurado** (`completarEstructurado`, que exige `json_object`) usa **siempre
  OpenAI**, porque el modo JSON garantizado es propio de OpenAI.

**Dependencia** (`pom.xml`): `dev.langchain4j:langchain4j-google-ai-gemini:1.0.0-beta1`
(la versión del core de LangChain4j que arrastra `quarkus-langchain4j-openai` 0.25.0, verificada
con `mvn dependency:tree`). El modelo de Gemini se construye a mano con
`GoogleAiGeminiChatModel.builder()` / `GoogleAiGeminiStreamingChatModel.builder()`.

**Configuración** (`application.properties`):
```
comprendia.gemini.api.clave=${GEMINI_API_KEY:}
comprendia.chat.modelo-por-defecto=openai
```
La clave de Gemini se inyecta como `Optional<String>` (la cadena vacía la interpreta el conversor
de SmallRye como `null`, lo que rompía el arranque con `@ConfigProperty String`).

**Backend:**
- `ChatGptServicio`: helpers `chat(proveedor, maxTokens, temp, json)` y
  `chatStream(proveedor, maxTokens, temp)` construyen y cachean el modelo por
  `(proveedor|params)`. `proveedor(...)` normaliza y **cae a OpenAI** si se pide Gemini sin clave.
  Nuevo `modelosDisponibles()` → `List<ModeloChatDTO>` (id, nombre, disponible).
- `ModeloChatDTO` (record) y endpoint REST `GET /api/modelos`.
- El parámetro `modelo` se propaga: `ConsultaConversacionDTO.modelo` →
  `RagServicio.responderConversacion` → `completarConversacion`, y por WebSocket
  `ChatWebSocket` → `completarConversacionStream`.

**Frontend** (Angular):
- `TranscripcionServicio.obtenerModelos()` (`GET /api/modelos`) e interfaz `ModeloChat`.
- Selector `<select>` sobre el input del chat (solo se muestra si hay >1 modelo); las opciones sin
  clave aparecen deshabilitadas ("no configurado").
- El modelo elegido se envía tanto en el payload del WebSocket como en la llamada HTTP de respaldo.

**Resultado:** backend compila y los 19 tests pasan; el frontend compila.

### Paso 4 — RAG con el EmbeddingStore de LangChain4j ✅

Hasta ahora el modelo de embeddings ya era de LangChain4j (Paso 1), pero la **recuperación**
(el *retriever*) era SQL artesanal dentro de `RagServicio` + el repositorio. Este paso sustituye
esa fontanería por las **abstracciones estándar de LangChain4j** (`EmbeddingStore`,
`EmbeddingSearchRequest/Result`, `TextSegment`, `Filter`), que es lo que propone la memoria TALENT.

**Método elegido — adaptador propio (Opción A):** en vez de usar `langchain4j-pgvector` (que
gestiona su propia tabla y obligaría a reprocesar todos los embeddings), implementamos un
`EmbeddingStore<TextSegment>` que **delega en la tabla pgvector existente**. Así no se reprocesa
nada, no se duplican datos, no hay dependencias nuevas (la interfaz vive en `langchain4j-core`) y
el comportamiento de cara al usuario es idéntico.

**Cambios:**
- **`servicio/rag/AlmacenEmbeddingsPgvector`** (nuevo): implementa `EmbeddingStore<TextSegment>`.
  Solo soporta `search(...)`: extrae el `videoId` del filtro
  `metadataKey("videoId").isEqualTo(id)`, formatea el `queryEmbedding` a la sintaxis de pgvector
  (`[v0,v1,...]`), llama a `buscarPorSimilitud` y mapea cada fila a un `EmbeddingMatch` con
  `score` = similitud coseno y un `TextSegment` cuyo `Metadata` lleva tiempos y orden. Los métodos
  `add*` lanzan `UnsupportedOperationException` (la ingesta sigue en `EmbeddingFragmentoServicio`).
- **`EmbeddingServicio`**: nuevo `generarEmbeddingLc(String)` que devuelve el `Embedding` de
  LangChain4j (sin convertir a `List<Double>`), para alimentar el `EmbeddingSearchRequest`.
- **`RagServicio`**: nuevo helper `recuperar(videoId, texto, limite)` que construye el
  `EmbeddingSearchRequest` (filtrado por vídeo) y delega en el almacén. Las tres rutas de
  recuperación (`responderLocal`, `responderConversacion`, `prepararConversacion`) dejan de hacer
  el embedding + SQL a mano y usan este helper. Se conservan la regla de "contexto completo si el
  vídeo es pequeño", la lógica de entidad reciente y las fuentes (momentos del vídeo).

**Lo que NO se toca:** esquema de BD, modelo de embeddings (`text-embedding-3-small`), ingesta de
embeddings, ni el frontend.

**Resultado:** compila, 19 tests verdes, y verificado en vivo por WebSocket que tanto OpenAI como
Gemini siguen respondiendo en streaming con las fuentes (tiempos) intactas.
