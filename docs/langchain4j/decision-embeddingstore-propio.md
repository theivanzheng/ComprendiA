# Decisión: `EmbeddingStore` propio sobre pgvector (no el oficial de LangChain4j)

> Registro de decisión técnica del Paso 4 de la migración a LangChain4j.
> Fecha: 2026-06-06 · Rama: `feat/langchain4j`

## Contexto

El Paso 4 consistía en sustituir la **recuperación** del RAG —hasta entonces SQL artesanal dentro
de `RagServicio`— por las abstracciones estándar de LangChain4j (`EmbeddingStore`,
`EmbeddingSearchRequest/Result`, `TextSegment`, `Filter`), tal y como propone la memoria técnica
del TALENT.

Para conectar el `EmbeddingStore` con PostgreSQL había dos caminos.

## Opciones consideradas

### Opción A — Adaptador propio sobre la tabla existente (ELEGIDA)
Implementar nosotros la interfaz `EmbeddingStore<TextSegment>` delegando en la tabla
`fragmentos_transcripcion` y el operador de distancia de `pgvector` que ya teníamos.

- ✅ No reprocesa los *embeddings* ya generados (coste cero de migración).
- ✅ No duplica almacenamiento: una sola tabla, la de siempre.
- ✅ Sin dependencias nuevas (la interfaz vive en `langchain4j-core`, ya presente).
- ✅ El resto del flujo RAG ya usa componentes estándar de LangChain4j.
- ⚠️ Hay que escribir el adaptador (≈120 líneas) y soportar solo lo que usamos (la búsqueda).

### Opción B — `langchain4j-pgvector` oficial (DESCARTADA)
Usar la implementación oficial del `EmbeddingStore` para pgvector.

- ✅ "De manual", cero código de adaptación.
- ❌ Gestiona **su propio esquema de tablas** → habría que migrar/duplicar los datos.
- ❌ Obligaría a **reprocesar todos los *embeddings*** (coste de API y tiempo).
- ❌ Dependencia adicional y un esquema paralelo que mantener.

## Decisión

Se elige la **Opción A**. La continuidad de los datos y el coste cero de migración pesan más que
el ahorro de escribir un adaptador. El objetivo del paso (usar las abstracciones de LangChain4j en
el RAG) se cumple igualmente, porque el recuperador trabaja contra la interfaz estándar
`EmbeddingStore`.

## Implementación (resumen)

- **`servicio/rag/AlmacenEmbeddingsPgvector`** — implementa `EmbeddingStore<TextSegment>`. Solo
  soporta `search(...)`: extrae el `videoId` del filtro `metadataKey("videoId").isEqualTo(id)`,
  formatea el vector de la consulta a la sintaxis de pgvector (`[v0,v1,...]`), ejecuta la búsqueda
  por similitud y devuelve cada resultado como `EmbeddingMatch` con `score` = similitud coseno y un
  `TextSegment` cuyo `Metadata` lleva los tiempos y el orden del fragmento. Los métodos `add*`
  lanzan `UnsupportedOperationException` (la ingesta sigue en `EmbeddingFragmentoServicio`).
- **`EmbeddingServicio.generarEmbeddingLc(...)`** — devuelve el `Embedding` nativo de LangChain4j
  para alimentar el `EmbeddingSearchRequest`.
- **`RagServicio.recuperar(...)`** — construye el `EmbeddingSearchRequest` (filtrado por vídeo) y
  delega en el almacén; las tres rutas de recuperación (local, conversación HTTP y WebSocket) lo
  usan. Se conservan la regla de "contexto completo si el vídeo es pequeño", la entidad reciente y
  las fuentes (momentos del vídeo).

No se toca el esquema de BD, el modelo de *embeddings* (`text-embedding-3-small`), la ingesta ni el
*frontend*.

## Verificación

Compila, 19 tests en verde, y comprobado en vivo por WebSocket que tanto OpenAI como Gemini
responden en streaming con las fuentes (tiempos) intactas.

---

## Anexo: ¿qué es `pgvector` y cómo lo "mejora" este cambio?

**`pgvector`** es una extensión de PostgreSQL que añade un **tipo de dato `vector`** y
**operadores de distancia** entre vectores (coseno `<=>`, L2, producto interno) a una base de datos
relacional normal. Sirve para guardar *embeddings* (los vectores numéricos que representan el
significado de cada fragmento) **en la misma base de datos** que el resto de los datos, y para
preguntar "dame los 8 fragmentos cuyo vector está más cerca del de esta pregunta" con una simple
consulta SQL ordenada por distancia. Evita montar una base de datos vectorial aparte (Pinecone,
Weaviate…).

**Importante:** este cambio (Paso 4) **no mejora `pgvector` en sí** —la base de datos y la consulta
de similitud son las mismas—. Lo que mejora es **cómo el código accede a esos vectores**:

| Aspecto | Antes (SQL artesanal) | Ahora (adaptador `EmbeddingStore`) |
|---|---|---|
| Quién hace la búsqueda | `RagServicio` montaba la cadena del vector y el SQL a mano, en tres sitios distintos | Un único adaptador que implementa la interfaz estándar de LangChain4j |
| Acoplamiento | El RAG conocía detalles de pgvector y del repositorio | El RAG habla con una **abstracción** (`EmbeddingStore`); pgvector queda oculto detrás |
| Formato de resultado | Filas crudas mapeadas a un DTO | `TextSegment` + `Metadata` (texto, tiempos, orden) + `score`, el formato común del *framework* |
| Intercambiabilidad | Cambiar de almacén implicaba reescribir el RAG | Se podría cambiar a otro `EmbeddingStore` (Pinecone, etc.) sin tocar el RAG |
| Alineación con la memoria TALENT | Parcial (solo el modelo de *embeddings* era de LangChain4j) | Completa: el RAG usa las piezas estándar del *framework* |

En una frase: **seguimos guardando y consultando los vectores con `pgvector` exactamente igual,
pero ahora lo hacemos a través de la interfaz estándar de LangChain4j**, lo que desacopla el RAG del
almacén concreto y deja la puerta abierta a cambiarlo en el futuro sin reescribir la lógica.
