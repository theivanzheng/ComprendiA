ComprendiA — Resumen técnico para despliegue en la nube

⸻

🧠 Descripción

ComprendiA es una aplicación educativa que transcribe vídeos de YouTube, genera embeddings por fragmento y permite búsqueda semántica sobre el contenido.

⸻

⚙️ Stack técnico

| Capa | Tecnología |
|------|-----------|
| Backend | Java 21 + Quarkus 3.x |
| Frontend | Angular 21 |
| Base de datos | PostgreSQL con extensión pgvector |
| Transcripción | yt-dlp + ffmpeg + OpenAI Whisper |
| Embeddings | OpenAI API (text-embedding-3-small) |
| Tests | H2 (en memoria) |

⸻

🔐 Variables de entorno requeridas

NUNCA incluir claves reales en el código o en los commits.

| Variable | Descripción |
|----------|-------------|
| OPENAI_API_KEY | Clave de la API de OpenAI |
| DATABASE_URL | URL JDBC de PostgreSQL |

Formato obligatorio de DATABASE_URL:

jdbc:postgresql://HOST/NOMBRE_BD?user=USUARIO&password=CONTRASEÑA&sslmode=require

⸻

📡 Endpoints disponibles

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | /api/salud | Comprobación de salud |
| POST | /api/transcripciones/youtube | Transcribir vídeo de YouTube |
| GET | /api/transcripciones | Historial paginado de vídeos |
| GET | /api/transcripciones/{id}/fragmentos | Fragmentos de un vídeo |
| GET | /api/transcripciones/{id}/buscar?pregunta=... | Búsqueda semántica |

⸻

🗄️ Esquema de base de datos

Tabla videos:
- id (PK)
- youtube_id
- titulo
- fecha_creacion
- fuente_transcripcion

Tabla fragmentos_transcripcion:
- id (PK)
- video_id (FK → videos)
- texto
- tiempo_inicio
- tiempo_fin
- orden_fragmento
- embedding_json (TEXT — almacena vector como JSON)

La columna embedding_json es migrable a pgvector con:

ALTER TABLE fragmentos_transcripcion
  ALTER COLUMN embedding_json TYPE vector(1536)
  USING embedding_json::vector;

⸻

🔍 Búsqueda semántica

Implementación actual (sin pgvector):
1. Se genera el embedding de la pregunta con OpenAI
2. Se recuperan todos los fragmentos con embedding del vídeo
3. Se calcula la similitud coseno en Java
4. Se devuelven los 5 fragmentos más similares

⸻

⏱️ Diagnóstico de rendimiento

El endpoint POST /api/transcripciones/youtube es síncrono y puede tardar 1–5 minutos.
Los logs del backend miden cada fase con el formato [Tiempo].

Fases medidas y timeouts:

| Fase | Clase | Timeout configurado | Tiempo típico |
|------|-------|---------------------|---------------|
| Descarga audio (yt-dlp) | AudioExtraccionServicio | 120s (ProcessBuilder) | 10–60s |
| Transcripción Whisper | TranscripcionAudioWhisperServicio | 5 min (HTTP) | 20–120s |
| Persistencia PostgreSQL | TranscripcionPersistenciaServicio | sin límite explícito | 500ms–5s |
| Embeddings por fragmento | EmbeddingFragmentoServicio | 30s por llamada (HTTP) | 200–800ms × N fragmentos |

Interpretación de los logs:
- yt-dlp > 60s → vídeo muy largo o conexión lenta
- Whisper > 120s → archivo de audio grande; considerar recortar el vídeo
- Persistencia > 5s → latencia de Neon/Supabase (posible cold start)
- Embeddings > 60s total → vídeo con muchos fragmentos

⸻

🖥️ Decisión UX: barra indeterminada

El endpoint actual es bloqueante y síncrono. No hay forma de reportar progreso real sin un sistema asíncrono.

Decisión tomada: mostrar barra de progreso indeterminada animada.
- No se muestra porcentaje falso
- Se muestran avisos de tardanza a los 60s y 180s
- El usuario sabe que el proceso sigue activo

⸻

🔜 Siguiente paso: sistema asíncrono (no implementado aún)

Cuando el tiempo de procesamiento sea inaceptable para el usuario, la solución correcta es:

POST /api/transcripciones/youtube
  → devuelve { idTrabajo: "uuid" } inmediatamente (HTTP 202)

GET /api/transcripciones/trabajos/{idTrabajo}
  → devuelve { estado: "EN_PROGRESO"|"COMPLETADO"|"ERROR", fase: "descargando"|"transcribiendo"|..., resultado: ... }

El frontend haría polling cada 3–5s sobre ese endpoint.

No se implementa en esta fase para mantener la arquitectura simple.

⸻

🧪 Perfil de tests

El fichero backend/src/test/resources/application.properties configura:
- H2 en memoria (sin necesidad de PostgreSQL)
- comprendia.embedding.habilitado=false (sin llamadas a OpenAI)
- comprendia.transcripcion.modo=simulada (sin yt-dlp/Whisper)

Los tests del backend pasan sin variables de entorno.

⸻

🚀 Arranque

Backend (requiere DATABASE_URL y OPENAI_API_KEY exportadas):

cd backend && mvn quarkus:dev

Frontend:

cd frontend && npm install && ng serve

⸻

⚠️ Recordatorios importantes

- Todo el código está en español (clases, métodos, variables, comentarios)
- Nunca incluir claves reales en application.properties ni en commits
- DATABASE_URL debe usar formato jdbc:postgresql://, no postgresql://
- Los tests no necesitan base de datos real ni API key de OpenAI
- No mostrar porcentajes de progreso falsos en el frontend
