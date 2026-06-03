# ComprendiA

Aplicación educativa que transcribe vídeos de YouTube, genera embeddings por fragmento y permite búsqueda semántica sobre el contenido.

## Estructura funcional prevista

- `Mis Cursos` representa el conjunto de cursos o asignaturas que tiene el usuario.
- Dentro de cada curso, el usuario puede tener varias clases.
- Cada una de esas clases corresponde a una grabación que ya haya sido procesada desde ComprendiA.
- Esto permite agrupar las clases analizadas no solo como historial general, sino también organizadas por curso.
- `Historial` representa todas las clases procesadas en orden cronológico, aunque todavía no estén categorizadas dentro de una asignatura.

### Pantalla de clase analizada

La pantalla que aparece después de pulsar `Analizar` sustituye por completo a la interfaz provisional de trabajo. Debe ser la vista principal para consultar una clase ya procesada o en procesamiento.

**Distribución principal:**
- Navbar superior igual que el de la home.
- Zona izquierda con el reproductor del vídeo como elemento principal.
- Timeline bajo el reproductor con capítulos.
- Panel derecho fijo con `Asistente ComprendiA`.
- Debajo del vídeo, metadatos editables de la clase.
- Secciones inferiores para `Conceptos clave`, `Resumen de la clase` y `Clases relacionadas`.

**Reproductor y capítulos:**
- El reproductor debe incluir una timeline con capítulos.
- Los capítulos serán una mezcla de capítulos generados automáticamente por IA y capítulos creados o ajustados manualmente por el usuario.
- La transcripción completa no se mostrará al usuario en esta pantalla, porque su uso principal es interno para analizar el vídeo.
- Los capítulos generados por IA se guardan como datos propios del vídeo, separados de los fragmentos de transcripción.
- Los fragmentos siguen siendo la base para RAG, búsqueda semántica y análisis interno.

**Asistente ComprendiA:**
- El chat aparece a la derecha del vídeo.
- El panel debe quedarse fijo al hacer scroll.
- Debe incluir sugerencias iniciales, por ejemplo `Resume la clase`, `Explícame este concepto` o preguntas similares.
- El usuario podrá lanzar preguntas desde los conceptos clave.

**Metadatos editables:**
- La asignatura y la fecha se muestran bajo el reproductor.
- Ambos campos deben ser editables.
- La asignatura sirve para categorizar la clase dentro de `Mis Cursos`.
- `Mis Cursos` debe llevar a una página real con un grid de asignaturas.
- Dentro de cada asignatura se mostrarán las clases procesadas que el usuario haya asignado a esa categoría.

**Conceptos clave:**
- Los conceptos clave se mostrarán como listado, no como tarjetas.
- Cada concepto tendrá una definición corta.
- Cada concepto tendrá un timestamp clicable para saltar al momento correspondiente del vídeo.
- Cada concepto permitirá lanzar una pregunta al chat.
- Los conceptos clave también se guardan como datos propios del vídeo y se consumen desde el frontend mediante endpoint.

**Resumen de la clase:**
- El resumen será corto.
- Debe explicar cómo empieza la clase, qué se hace durante la sesión y con qué resultado termina.

**Clases relacionadas:**
- La sección se llamará `Clases relacionadas`.
- De momento puede usar placeholders.
- En el futuro mostrará clases de la misma asignatura con fechas cercanas al vídeo actual.
- Al pulsar una clase relacionada, se abrirá esta misma pantalla de detalle sustituyendo la clase actual.

**Estado de procesamiento:**
- Si el vídeo todavía se está analizando, el estado debe aparecer bajo el reproductor de forma sutil.
- Debe mostrar texto, fase actual y botón para cancelar.

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
| POST | `/api/transcripciones/youtube/{idTrabajo}/cancelar` | Cancelar un trabajo de análisis en curso |
| GET | `/api/transcripciones` | Historial paginado de vídeos |
| GET | `/api/transcripciones/{id}/fragmentos` | Fragmentos de un vídeo |
| GET | `/api/transcripciones/{id}/capitulos` | Capítulos generados para navegar la clase |
| GET | `/api/transcripciones/{id}/conceptos` | Conceptos clave detectados en la clase |
| GET | `/api/transcripciones/{id}/buscar?pregunta=...` | Búsqueda semántica |

---

## Arquitectura del procesamiento asíncrono

El endpoint `POST /api/transcripciones/youtube` devuelve un `idTrabajo` inmediatamente (HTTP 202) y lanza el pipeline en un hilo virtual de Java 21. El frontend hace polling cada 2.5 segundos al endpoint de estado y actualiza un stepper visual con las fases:

```
DESCARGANDO → TRANSCRIBIENDO → GUARDANDO → EMBEDDINGS → COMPLETADO
```

El trabajo también puede terminar como `CANCELADO` o `ERROR`.

La cancelación se realiza desde `POST /api/transcripciones/youtube/{idTrabajo}/cancelar`. El backend marca el trabajo como cancelado, interrumpe el hilo virtual asociado y el pipeline comprueba ese estado entre fases críticas. Si ya se había guardado parcialmente un vídeo, se eliminan sus conceptos, capítulos, fragmentos y el registro del vídeo.

Después de generar embeddings, el backend ejecuta un análisis educativo de la clase:

- Agrupa la transcripción en capítulos semánticos con `titulo`, `descripcion`, `tiempoInicio` y `tiempoFin`.
- Extrae conceptos clave con `nombre`, `definicion` y `tiempoInicio`.
- Guarda los capítulos en `capitulos_video`.
- Guarda los conceptos en `conceptos_clave_video`.
- Si OpenAI no está disponible o devuelve una respuesta inválida, se usa un fallback local basado en bloques temporales de fragmentos para no romper el procesamiento.

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
