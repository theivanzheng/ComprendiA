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

### Paso 2 — Chat con LangChain4j (pendiente)
<!-- ChatGptServicio -> ChatLanguageModel / StreamingChatLanguageModel -->

### Paso 3 — RAG con LangChain4j (opcional, pendiente)
