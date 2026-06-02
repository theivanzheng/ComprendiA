# ComprendiA

Aplicación educativa que transcribe vídeos de YouTube, genera embeddings por fragmento y permite búsqueda semántica sobre el contenido.

---

## Requisitos previos

Instala las herramientas del sistema (solo la primera vez):

```bash
brew install yt-dlp ffmpeg
```

Comprueba que están disponibles:

```bash
which yt-dlp && which ffmpeg && which ffprobe
```

También necesitas:
- Java 21
- Maven
- Node.js + Angular CLI (`npm install -g @angular/cli`)
- Una cuenta en [Neon](https://neon.tech) con una base de datos PostgreSQL creada
- Una clave de OpenAI (`sk-...`)

---

## Arranque en local

### 1. Configurar las variables de entorno del backend

Edita `backend/.env` con tus credenciales reales:

```
OPENAI_API_KEY=sk-...
DATABASE_URL=jdbc:postgresql://HOST/NOMBRE_BD?user=USUARIO&password=CONTRASEÑA&sslmode=require
```

> La `DATABASE_URL` la encuentras en el panel de Neon → tu proyecto → "Connection string". Cambia el prefijo `postgresql://` por `jdbc:postgresql://`.

---

### 2. Arrancar el backend

En una terminal:

```bash
cd backend
source .env
mvn quarkus:dev
```

Espera hasta ver `Listening on: http://localhost:8080`.

Comprueba que responde:

```bash
curl http://localhost:8080/api/salud
```

---

### 3. Arrancar el frontend

En otra terminal:

```bash
cd frontend
npm install
ng serve
```

Abre el navegador en **http://localhost:4200**

---

## Probar el flujo completo

**Desde el navegador:**
1. Pega una URL de YouTube y pulsa "Procesar vídeo"
2. Espera la barra de progreso (puede tardar 1–5 minutos)
3. Cuando termine, escribe una pregunta para buscar semánticamente

**Desde la terminal:**

```bash
# Transcribir un vídeo
curl -X POST http://localhost:8080/api/transcripciones/youtube \
  -H "Content-Type: application/json" \
  -d '{"urlVideo":"https://www.youtube.com/watch?v=YavB75vLSuc"}'

# Ver historial de vídeos
curl http://localhost:8080/api/transcripciones

# Búsqueda semántica (sustituye {id} por el id devuelto)
curl "http://localhost:8080/api/transcripciones/{id}/buscar?pregunta=¿De qué trata el vídeo?"
```

---

## Ejecutar los tests

Los tests no necesitan base de datos ni API key de OpenAI:

```bash
cd backend
mvn test
```

---

## Autor

Iván Zheng
