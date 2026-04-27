ComprendiA

Aplicación educativa inteligente para analizar vídeos/clases grabadas mediante IA.

⸻

📌 Descripción

ComprendiA permite:

* Obtener la transcripción de un vídeo de YouTube
* Fragmentar el contenido en bloques temporales
* Persistir los datos en PostgreSQL (Neon/Supabase)
* Generar embeddings mediante OpenAI
* Preparar los datos para búsqueda semántica (RAG)

⸻

🧠 Arquitectura

Flujo actual:

YouTube → Descarga audio → Whisper → Fragmentación → PostgreSQL → Embeddings → (futuro: búsqueda semántica)

⸻

⚙️ Stack

* Backend: Java 21 + Quarkus
* Frontend: Angular
* Transcripción: yt-dlp + ffmpeg + OpenAI Whisper
* IA / embeddings: OpenAI API
* RAG (futuro): LangChain4j
* Base de datos: PostgreSQL (Neon / Supabase)
* Tests: H2

⸻

📊 Estado actual

✔ Backend Quarkus funcional
✔ Frontend Angular funcional
✔ Endpoint /api/salud
✔ Integración con YouTube
✔ Descarga de audio (yt-dlp)
✔ Procesamiento con ffmpeg
✔ Transcripción real (Whisper)
✔ Fragmentación con timestamps
✔ Persistencia en PostgreSQL
✔ Generación de embeddings por fragmento

⬜ Búsqueda semántica
⬜ RAG completo
⬜ UI avanzada

⸻

📁 Estructura del proyecto

Comprendia/
├── backend/    # API REST con Quarkus
├── frontend/   # Interfaz con Angular
├── docs/       # Documentación técnica
└── README.md

⸻

🧰 Requisitos locales

brew install yt-dlp
brew install ffmpeg

Comprobar:

which yt-dlp
which ffmpeg
which ffprobe

⸻

🔐 Variables de entorno

export OPENAI_API_KEY="sk-..."
export DATABASE_URL="jdbc:postgresql://HOST/DB?user=USER&password=PASS&sslmode=require"

Comprobar:

echo $OPENAI_API_KEY
echo $DATABASE_URL

⸻

⚠️ IMPORTANTE — Formato de DATABASE_URL

Neon da:

postgresql://user:pass@host/db

Pero Quarkus necesita:

jdbc:postgresql://host/db?user=USER&password=PASS&sslmode=require

⸻

⚙️ Configuración backend

En application.properties:

comprendia.openai.api.clave=${OPENAI_API_KEY}
comprendia.transcripcion.modo=whisper
quarkus.datasource.jdbc.url=${DATABASE_URL}

⸻

🚀 Ejecutar backend

cd backend
mvn quarkus:dev

Backend:
http://localhost:8080

⸻

🚀 Ejecutar frontend

cd frontend
npm install
ng serve

Frontend:
http://localhost:4200

⸻

📡 Endpoints

🔹 Salud

curl http://localhost:8080/api/salud

⸻

🔹 Transcribir vídeo

curl -X POST http://localhost:8080/api/transcripciones/youtube \
  -H "Content-Type: application/json" \
  -d '{"urlVideo":"https://www.youtube.com/watch?v=YavB75vLSuc"}'

⸻

🔹 Obtener historial

curl http://localhost:8080/api/transcripciones

⸻

🔹 Obtener fragmentos

curl http://localhost:8080/api/transcripciones/{id}/fragmentos

⸻

🗄️ Base de datos

Tabla: videos

* id
* youtube_id
* titulo
* fecha_creacion
* fuente_transcripcion

⸻

Tabla: fragmentos_transcripcion

* id
* video_id
* texto
* tiempo_inicio
* tiempo_fin
* orden_fragmento
* embedding_json (TEXT)

⸻

🤖 Embeddings

* Se generan automáticamente tras la transcripción
* Cada fragmento tiene su embedding
* Se almacenan como JSON

Ejemplo:

SELECT id, LEFT(embedding_json, 80)
FROM fragmentos_transcripcion
WHERE embedding_json IS NOT NULL;

⸻

⚠️ Problemas técnicos resueltos

1. Formato JDBC incorrecto

Quarkus no acepta postgresql://
✔ Solución: usar jdbc:postgresql://

⸻

2. Auto-invocación CDI (@Transactional roto)

Llamar this.metodo() rompe la transacción
✔ Solución: mover lógica a otro bean

⸻

3. Nombre de columna incorrecto

@Column(name = "embedding_json")

⸻

4. Fallos silenciosos de OpenAI

* 401 por clave incorrecta
* Ahora se loguea correctamente

⸻

🧪 Verificación

SELECT COUNT(*) 
FROM fragmentos_transcripcion 
WHERE embedding_json IS NOT NULL;

Debe devolver > 0

⸻

💡 Diseño clave

* Embeddings guardados como TEXT (compatible con H2)
* Migrable a pgvector
* Fallos de OpenAI no rompen el flujo
* Transacciones separadas

⸻

🔜 Próximos pasos

1. Búsqueda semántica (pgvector)
2. Endpoint de búsqueda
3. Integración RAG
4. UI de resultados
5. Respuestas con timestamp

⸻

👨‍💻 Autor

Iván Zheng

⸻

