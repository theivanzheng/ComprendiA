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
2. Observa el progreso paso a paso (descarga → transcripción → guardado → embeddings)
3. Cuando termine, escribe una pregunta para buscar semánticamente

**Formatos de URL admitidos:**
- `https://www.youtube.com/watch?v=ID`
- `https://youtu.be/ID`
- `https://www.youtube.com/shorts/ID`
- `https://m.youtube.com/watch?v=ID` (móvil)
- Con timestamp: `?v=ID&t=30s`
- Sin protocolo: `www.youtube.com/watch?v=ID`

**Desde la terminal:**

```bash
# Iniciar transcripción (devuelve idTrabajo inmediatamente)
curl -X POST http://localhost:8080/api/transcripciones/youtube \
  -H "Content-Type: application/json" \
  -d '{"urlVideo":"https://www.youtube.com/watch?v=YavB75vLSuc"}'
# → {"idTrabajo":"uuid"}

# Consultar estado del trabajo
curl http://localhost:8080/api/transcripciones/youtube/{idTrabajo}
# → {"fase":"DESCARGANDO"|"TRANSCRIBIENDO"|"GUARDANDO"|"EMBEDDINGS"|"COMPLETADO"|"ERROR"}

# Ver historial de vídeos
curl http://localhost:8080/api/transcripciones

# Búsqueda semántica (sustituye {id} por el id del vídeo)
curl "http://localhost:8080/api/transcripciones/{id}/buscar?pregunta=¿De qué trata el vídeo?"
```

---

## Endpoints disponibles

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/api/salud` | Comprobación de salud |
| POST | `/api/transcripciones/youtube` | Iniciar transcripción (async, devuelve `idTrabajo`) |
| GET | `/api/transcripciones/youtube/{idTrabajo}` | Estado del trabajo en curso |
| GET | `/api/transcripciones` | Historial paginado de vídeos |
| GET | `/api/transcripciones/{id}/fragmentos` | Fragmentos de un vídeo |
| GET | `/api/transcripciones/{id}/buscar?pregunta=...` | Búsqueda semántica |

---

## Arquitectura del procesamiento asíncrono

El endpoint `POST /api/transcripciones/youtube` devuelve un `idTrabajo` inmediatamente (HTTP 202) y lanza el pipeline en un hilo virtual de Java 21. El frontend hace polling cada 2.5 segundos al endpoint de estado y actualiza un stepper visual con las fases:

```
DESCARGANDO → TRANSCRIBIENDO → GUARDANDO → EMBEDDINGS → COMPLETADO
```

---

## Ejecutar los tests

Los tests no necesitan base de datos ni API key de OpenAI:

```bash
cd backend
mvn test
```

---

## Ideas para implementaciones futuras

### Reproductor integrado con marcas de capítulo IA

El objetivo es sustituir el embed básico de YouTube por un reproductor interactivo donde la barra de progreso muestre marcas visuales en los timestamps de cada fragmento analizado por la IA —similar a los capítulos de YouTube, pero generados automáticamente a partir de la transcripción.

**Comportamiento esperado:**
- El vídeo se reproduce directamente en la aplicación
- La barra de progreso tiene marcas de color en cada fragmento indexado
- Al llegar a una marca, se resalta automáticamente el fragmento correspondiente en la transcripción
- Al hacer clic en un fragmento de la transcripción, el vídeo salta a ese timestamp

**Tecnología necesaria:**
- YouTube IFrame API (`YT.Player`) para controlar la reproducción desde JavaScript
- Evento `onStateChange` y `getCurrentTime()` para sincronizar posición con fragmentos
- SVG o Canvas overlay sobre la barra de progreso nativa para pintar las marcas
- Angular service que suscriba al progreso del reproductor y actualice el fragmento activo

**Datos ya disponibles:** cada fragmento tiene `tiempoInicio` y `tiempoFin` en segundos, listos para calcular la posición proporcional sobre la barra.

---

## Autor

Iván Zheng
