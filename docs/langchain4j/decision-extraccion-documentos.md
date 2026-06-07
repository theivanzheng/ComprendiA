# Funcionalidad: documentos del curso (PDF y otros)

> Documentación de la feature **documentos del curso** + decisión de extracción con Apache Tika.
> Estado: **implementada**. Rama: `feat/langchain4j`.
> Alcance elegido: documentos asociados a la **clase/vídeo** (reutiliza el chat por vídeo existente).

## Contexto

El RAG actual solo indexa transcripciones de vídeo. La memoria técnica del TALENT propone
**combinar vídeos con documentos del curso** (apuntes, presentaciones, artículos). Para ello hay que
**extraer el texto** de esos documentos antes de trocearlo, generar *embeddings* e indexarlo en la
misma tabla `pgvector` (reutilizando la tubería del Paso 4).

La pregunta de esta decisión es: **¿con qué método extraemos el texto?**

Principio de coste: la extracción se hace en el *backend* con una **librería local**, sin pasar el
documento por un LLM. Solo los *embeddings* (pago único y barato) y, después, los ~8 fragmentos
relevantes por pregunta tocan las APIs. Así el tamaño del documento **no** dispara el gasto de tokens.

## Opciones consideradas

### A — Apache Tika, vía `langchain4j-document-parser-apache-tika` (ELEGIDA)
Extractor universal de texto. Un solo `DocumentParser` que detecta el formato y devuelve el texto.

- ✅ **"Navaja suiza":** un único componente cubre **PDF, DOCX, PPTX, TXT, HTML, RTF, ODT…**. El
  usuario habló de "PDFs **y otros documentos**"; con Tika no hay que escribir un parser por formato.
- ✅ **Local y gratis** (cero tokens de LLM).
- ✅ **Coherente con la arquitectura:** devuelve un `Document` de LangChain4j → `DocumentSplitter` →
  `EmbeddingModel` → la **misma** tabla pgvector del Paso 4. Reutiliza toda la tubería.
- ⚠️ Arrastra más dependencias que PDFBox (Tika incluye soporte para muchos formatos).
- ❌ Como cualquier extractor de texto, **no hace OCR**: en PDFs escaneados (imágenes) devuelve vacío.

### B — Apache PDFBox, vía `langchain4j-document-parser-apache-pdfbox` (descartada para el caso general)
Alternativa más ligera que usa directamente PDFBox.

- ✅ Menos dependencias (jar final más ligero), excelente con PDF de texto digital puro.
- ❌ **Solo PDF.** Para DOCX/PPTX habría que añadir otra librería → se pierde la ventaja de "un solo
  método". Por eso se descarta como solución general (aunque Tika usa PDFBox por debajo para los PDF).

### C — Amazon Textract, vía `langchain4j-document-parser-amazon-textract` (futuro)
Servicio gestionado de AWS basado en IA.

- ✅ Hace **OCR** (PDFs escaneados) y conserva **tablas y formularios** complejos de forma nativa.
- ❌ **No es local:** requiere llamadas a la API de AWS, con **coste por página** y una cuenta/SDK de
  AWS que montar y mantener. Excesivo para el alcance del TFG.

### D — Enfoque multimodal: páginas como imágenes al LLM (futuro)
Convertir cada página a imagen (con PDFBox) y pasarla a un modelo multimodal (Gemini Flash / GPT-4o)
usando `AiMessage` + `ImageContent` de LangChain4j.

- ✅ Captura *layouts* complejos, infografías, diagramas y documentos escaneados donde el texto plano
  pierde el contexto visual.
- ❌ **Caro en tokens** (cada página es una imagen que consume contexto del modelo), justo lo que
  queremos evitar en este proyecto. Va contra el principio de coste contenido.

### E — Herramientas externas (CLI/API especializadas, p. ej. para tablas anidadas o doble columna)
Procesar el PDF fuera de Java antes de meterlo en LangChain4j.

- ✅ Mejor para estructuras muy complejas (reportes financieros, doble columna) que confunden a los
  parsers tradicionales.
- ❌ Añade una pieza externa al *pipeline* y complejidad de despliegue. Innecesario para apuntes,
  presentaciones y artículos típicos de una asignatura.

## Decisión

Se elige **A — Apache Tika** (`langchain4j-document-parser-apache-tika`). Es la opción que mejor
equilibra **cobertura de formatos**, **coste cero de tokens**, **simplicidad** y **coherencia** con
la arquitectura RAG ya construida. Funciona como una *navaja suiza*: un solo parser para todos los
documentos de texto que un alumno va a subir.

## Alcance inicial y limitaciones conocidas

- **Incluido:** documentos **con texto** (PDF digitales, Word, PowerPoint exportado, artículos).
- **Fuera (mejora futura):** **OCR** de PDFs escaneados → se haría con **Textract (C)** o un OCR local
  (Tesseract). **Multimodal (D)** queda como vía para documentos muy visuales. Ambos se documentan
  como líneas futuras, no como limitación oculta (honestidad de cara a la defensa).

## Encaje con lo ya hecho (Paso 4)

La extracción es lo **único nuevo** del *pipeline*; a partir del texto, se reutiliza todo:
`DocumentSplitter` → `EmbeddingModel` (`text-embedding-3-small`) → un `EmbeddingStore` propio sobre
pgvector, idéntico en patrón al del Paso 4.

---

## Implementación (lo realmente construido)

**Alcance:** los documentos cuelgan de una **clase/vídeo**. El chat de esa clase (que ya existía,
por vídeo) pasa a responder combinando **transcripción + documentos de esa clase**. Se descartó el
nivel "asignatura" porque exigiría un chat nuevo que hoy no existe.

**Economía de tokens (lo que más preocupaba):** subir un documento solo cuesta un pago único y
mínimo de *embeddings*; responder preguntas cuesta **lo mismo que sobre un vídeo**, porque al modelo
de chat solo le llegan los ~8 fragmentos de transcripción + hasta 4 extractos de documento más
relevantes, nunca el documento entero.

### Flujo de subida (ingesta)
```
PDF/Word/PPT/TXT → Apache Tika (texto) → DocumentSplitters.recursive(900,150) (trozos)
                 → EmbeddingModel (1 vector/trozo) → tabla fragmentos_documento (pgvector)
```

### Flujo de consulta (chat)
```
Pregunta → embedding → AlmacenEmbeddingsPgvector (transcripción) ─┐
                     → AlmacenDocumentosPgvector (documentos) ────┴→ contexto combinado → LLM
```
Los extractos de documento se marcan en el contexto como `[Documento: nombre]` para que el modelo
sepa de dónde salen y pueda citarlos.

### Backend (archivos)
| Archivo | Rol |
|---|---|
| `entidad/DocumentoClase` | Metadatos del archivo subido (nombre, tipo, nº fragmentos), ligado al vídeo |
| `entidad/FragmentoDocumento` | Trozo de documento + embedding (`vector`), ligado a documento y vídeo |
| `repositorio/DocumentoClaseRepositorio` | Listado por vídeo |
| `repositorio/FragmentoDocumentoRepositorio` | Búsqueda por similitud, guardado de embedding, borrado |
| `servicio/ExtraccionDocumentoServicio` | Tika + `DocumentSplitters` → lista de trozos |
| `servicio/DocumentoServicio` | Orquesta subida (extraer+trocear+embeber), listar, eliminar |
| `servicio/rag/AlmacenDocumentosPgvector` | `EmbeddingStore<TextSegment>` sobre `fragmentos_documento` |
| `servicio/RagServicio` | `recuperarDocumentos` + `bloqueDocumentos`, añadidos al contexto del chat |
| `servicio/ChatGptServicio` | Regla nueva en el prompt: usar y citar `[Documento: …]` |
| `recurso/DocumentoRecurso` | `POST/GET /transcripciones/{id}/documentos`, `DELETE /documentos/{id}` |

**Dependencias añadidas:** `langchain4j` (splitters) y `langchain4j-document-parser-apache-tika`
(extracción). ⚠️ Como tocan el `pom.xml`, **requieren reiniciar el backend** (el *live reload* de
Quarkus no recarga dependencias).

### Frontend (archivos)
| Archivo | Cambio |
|---|---|
| `servicios/transcripcion.servicio.ts` | `DocumentoClase`, `listarDocumentos`, `subirDocumento` (multipart), `eliminarDocumento` |
| `app.ts` | Estado `documentos`, carga al abrir clase, subir/eliminar |
| `app.html` | Sección "Documentos del curso" en la vista de clase (subir, listar, borrar) |
| `app.css` | Estilos del panel |

### Limitaciones conocidas (mejora futura)
- **PDF escaneados (imágenes):** Tika no hace OCR → se rechaza la subida con un mensaje claro. OCR
  (Tesseract/Textract) queda como línea futura.
- Los documentos **no aparecen como "fuentes" clicables** en el chat (no tienen marca de tiempo); se
  usan como contexto y el modelo puede mencionar el nombre del documento. Mostrar el documento como
  fuente con su nombre sería una mejora menor futura.
