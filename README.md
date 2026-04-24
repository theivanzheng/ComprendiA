ComprendiA

Aplicación educativa inteligente para analizar vídeos/clases grabadas mediante IA.

Stack
	•	Backend: Java 21 + Quarkus
	•	Frontend: Angular
	•	Transcripción: yt-dlp + ffmpeg + OpenAI Whisper
	•	IA/RAG futuro: LangChain4j
	•	Base de datos futura: Supabase/PostgreSQL + pgvector

Estado actual
	•	Backend Quarkus funcional
	•	Frontend Angular funcional
	•	Endpoint /api/salud
	•	Integración con enlaces de YouTube
	•	Descarga de audio con yt-dlp
	•	Conversión/procesamiento con ffmpeg
	•	Transcripción real con OpenAI Whisper
	•	Fragmentos con timestamps
	•	Persistencia en Supabase/PostgreSQL
	•	Embeddings
	•	Motor RAG con LangChain4j

Estructura del proyecto

Comprendia/
├── backend/    # API REST con Quarkus
├── frontend/   # Interfaz con Angular
├── docs/       # Documentación técnica
└── README.md

Requisitos locales

brew install yt-dlp
brew install ffmpeg

Comprobar instalación:

which yt-dlp
which ffmpeg
which ffprobe

Configuración de OpenAI

La API key no debe guardarse en el código.

export OPENAI_API_KEY="tu_api_key"

Comprobar:

echo $OPENAI_API_KEY

En backend/src/main/resources/application.properties:

comprendia.openai.api.clave=${OPENAI_API_KEY}
comprendia.transcripcion.modo=whisper

Modos disponibles:

comprendia.transcripcion.modo=simulada
comprendia.transcripcion.modo=scraping
comprendia.transcripcion.modo=whisper

Ejecutar backend

cd backend
mvn quarkus:dev

Backend:

http://localhost:8080

Ejecutar frontend

cd frontend
npm install
ng serve

Frontend:

http://localhost:4200

Probar endpoint de salud

curl http://localhost:8080/api/salud

Probar transcripción real

Con backend arrancado en una terminal, ejecutar en otra:

curl -X POST http://localhost:8080/api/transcripciones/youtube \
  -H "Content-Type: application/json" \
  -d '{"urlVideo":"https://www.youtube.com/watch?v=YavB75vLSuc"}'

Respuesta esperada:

{
  "idVideo": "YavB75vLSuc",
  "titulo": "Transcripción de audio - YavB75vLSuc",
  "fragmentos": [
    {
      "texto": "...",
      "tiempoInicio": 0.0,
      "tiempoFin": 4.42
    }
  ],
  "fuenteTranscripcion": "REAL"
}

Guardar resultado en archivo

curl -s -X POST http://localhost:8080/api/transcripciones/youtube \
  -H "Content-Type: application/json" \
  -d '{"urlVideo":"https://www.youtube.com/watch?v=ub47hbys0WM"}' \
  > transcripcion-8min.json

Ver resultado:

cat transcripcion-8min.json | jq .

Próximos pasos
	1.	Conectar Supabase/PostgreSQL.
	2.	Guardar vídeos y fragmentos.
	3.	Generar embeddings.
	4.	Implementar búsqueda semántica.
	5.	Añadir RAG con LangChain4j.
	6.	Devolver respuestas con referencia al minuto exacto.