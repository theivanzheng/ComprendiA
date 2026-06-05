# ComprendiA

Aplicación educativa que transcribe vídeos de YouTube, genera embeddings por fragmento y permite búsqueda semántica sobre el contenido.

## Cambios integrados recientemente

- La vista de clase ya tiene URL propia mediante hash routing: `#/clase/{id}`. Al refrescar, la aplicación vuelve a cargar esa clase desde el backend.
- El backend expone `GET /api/transcripciones/{id}` para recuperar una clase concreta sin depender del historial cargado en memoria.
- Los metadatos de clase se persisten en backend: `asignatura`, `profesor`, `fechaClase` y `completado`.
- La asignatura y el profesor se editan desde PILs tipo Notion, permitiendo elegir una opción existente o crear una nueva.
- La fecha de clase es editable y se guarda junto al vídeo.
- La duración se calcula automáticamente desde los tiempos/fragmentos disponibles; no la introduce el usuario.
- El estado `Completado` ya no depende de `localStorage`: se guarda en backend y puede marcarse manualmente desde la pantalla de clase.
- Si el reproductor de YouTube emite el evento de vídeo terminado, la clase se marca automáticamente como completada.
- Las tarjetas de la home y de clases relacionadas usan thumbnails reales de YouTube con overlay oscuro para mantener legibilidad.
- El chat diferencia mejor entre preguntas globales de resumen y preguntas concretas sobre fragmentos, usando contexto global de capítulos, conceptos y fragmentos representativos cuando corresponde.
- El input del chat se limpia al enviar y el botón de envío muestra una animación de procesamiento.
- **Chat conversacional del vídeo:** el asistente mantiene un hilo con memoria corta (últimas 5–10 interacciones, solo en frontend, no persistido) que resuelve referencias implícitas ("el móvil", "y después?"). Nuevo endpoint `POST /api/transcripciones/{id}/conversar` que recibe el historial reciente y una entidad de contexto para enriquecer la búsqueda semántica.
- **Enlace al vídeo original de YouTube:** pill discreto "Fuente: YouTube ↗" en la cabecera de la clase.
- **Menú de tres puntos en las tarjetas de `Mis Cursos`:** editar y eliminar asignatura desde el grid (reutilizando el modal de edición y el flujo de borrado con confirmación por nombre).
- **Autoasignación sugerida de asignatura:** al terminar el análisis, el sistema asigna automáticamente una asignatura (por canal de YouTube o similitud semántica, creando una nueva si no hay candidata) y la marca como sugerencia. El pill muestra `Asignatura: X (sugerencia)`; al cambiarla a mano deja de ser sugerencia. "sugerencia" es solo metadato visual, nunca forma parte del nombre real.
- **Logs de tiempos en la clasificación:** cada fase de la autoasignación (`[Clasificacion][Tiempo]`) registra su duración para localizar cuellos de botella.

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
- En la cabecera de la clase, PILs editables para asignatura, profesor y fecha.
- Secciones inferiores para `Resumen de la clase`, `Conceptos clave` y `Clases relacionadas`.

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
- La asignatura, el profesor y la fecha se muestran como PILs en la cabecera de la clase.
- La asignatura y el profesor usan un selector tipo Notion: se puede elegir una opción existente o escribir una nueva.
- La duración se muestra como dato calculado del vídeo y no se edita manualmente.
- El estado de completado se puede marcar manualmente desde los botones del profesor junto al reproductor.
- La asignatura sirve para categorizar la clase dentro de `Mis Cursos`.
- `Mis Cursos` debe llevar a una página real con un grid de asignaturas.
- Dentro de cada asignatura se mostrarán las clases procesadas que el usuario haya asignado a esa categoría.

**Conceptos clave:**
- Los conceptos clave se muestran como tarjetas compactas en varias columnas.
- La sección incluye buscador para filtrar conceptos por título o definición.
- Cada concepto tendrá una definición corta.
- Cada concepto tendrá un timestamp clicable para saltar al momento correspondiente del vídeo.
- Cada concepto permitirá lanzar una pregunta al chat.
- Los conceptos clave también se guardan como datos propios del vídeo y se consumen desde el frontend mediante endpoint.

**Resumen de la clase:**
- El resumen será corto.
- Debe explicar cómo empieza la clase, qué se hace durante la sesión y con qué resultado termina.
- Actualmente se coloca antes de `Conceptos clave` para que el usuario entienda primero el contexto global de la clase.

**Clases relacionadas:**
- La sección se llamará `Clases relacionadas`.
- De momento puede usar placeholders.
- En el futuro mostrará clases de la misma asignatura con fechas cercanas al vídeo actual.
- Al pulsar una clase relacionada, se abrirá esta misma pantalla de detalle sustituyendo la clase actual.

**Navegación real:**
- La home, cursos e historial usan rutas hash simples.
- Una clase concreta se abre en `#/clase/{id}`.
- Esto evita que un refresh vuelva siempre a la home.

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
1. Pega una URL de YouTube y pulsa `Analizar`.
2. Observa el progreso paso a paso: descarga, transcripción, guardado, embeddings y análisis educativo.
3. Al terminar, se abre la clase en `#/clase/{id}`.
4. Edita asignatura, profesor o fecha desde los PILs superiores.
5. Usa el chat para preguntas globales, como `Resume la clase`, o preguntas concretas sobre un concepto.
6. Marca la clase como completada manualmente o espera a terminar el vídeo.

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
| GET | `/api/transcripciones/{id}` | Detalle/resumen de una clase concreta |
| DELETE | `/api/transcripciones/{id}` | Eliminar una clase y todos sus datos asociados |
| PATCH | `/api/transcripciones/{id}/metadata` | Actualizar asignatura (`idAsignatura`), profesor (`idProfesor`), fecha de clase, completado o `resumen` |
| PATCH | `/api/transcripciones/{id}/titulo` | Actualizar el título editable de una clase |
| GET | `/api/transcripciones/{id}/fragmentos` | Fragmentos de un vídeo |
| GET | `/api/transcripciones/{id}/capitulos` | Capítulos generados para navegar la clase |
| POST | `/api/transcripciones/{id}/capitulos` | Crear un capítulo manual |
| GET | `/api/transcripciones/{id}/conceptos` | Conceptos clave detectados en la clase |
| POST | `/api/transcripciones/{id}/conceptos` | Crear un concepto manual |
| GET | `/api/transcripciones/{id}/buscar?pregunta=...` | Búsqueda semántica |
| GET | `/api/transcripciones/{id}/responder?pregunta=...` | Respuesta del asistente ComprendiA con RAG |
| POST | `/api/transcripciones/{id}/conversar` | Chat conversacional con memoria corta (`pregunta`, `historial`, `entidadReciente`) |
| PATCH | `/api/capitulos/{id}` | Editar un capítulo |
| DELETE | `/api/capitulos/{id}` | Eliminar un capítulo |
| PATCH | `/api/conceptos/{id}` | Editar un concepto clave |
| DELETE | `/api/conceptos/{id}` | Eliminar un concepto clave |
| GET | `/api/asignaturas` | Listar asignaturas |
| POST | `/api/asignaturas` | Crear asignatura (acepta `idProfesor`) |
| GET | `/api/asignaturas/{id}` | Detalle de asignatura con clases, conceptos y horas |
| PATCH | `/api/asignaturas/{id}` | Editar asignatura (acepta `idProfesor`) |
| DELETE | `/api/asignaturas/{id}` | Eliminar asignatura (requiere `confirmacionNombre`) |
| GET | `/api/asignaturas/{id}/buscar?pregunta=...` | Búsqueda semántica dentro de la asignatura |
| GET | `/api/profesores` | Listar profesores |
| POST | `/api/profesores` | Crear profesor (`nombre`, `email` opcional) |

---

## Modelo de datos

| Entidad | Tabla | Campos principales | Relaciones |
|---------|-------|--------------------|------------|
| `Profesor` | `profesores` | nombre, email, fechaCreacion, fechaActualizacion | 1—N Asignatura, 1—N Video |
| `Asignatura` | `asignaturas` | nombre, descripcion, profesor (texto compat.), fechaCreacion, fechaActualizacion, **canalYoutubeId**, **canalYoutubeNombre**, **palabrasClave**, **embeddingResumen** (JSON) | N—1 Profesor (`profesorObj`, principal); 1—N Video |
| `Video` | `videos` | youtubeId, titulo, **resumen**, asignatura (texto compat.), profesor (texto compat.), fechaClase, completado, fuenteTranscripcion, fechaCreacion, **canalYoutubeId**, **canalYoutubeNombre**, **asignaturaSugerida**, **criterioAsignacion** (CANAL/SEMANTICA/MANUAL/NINGUNO) | N—1 Asignatura (`asignaturaObj`); N—1 Profesor (`profesorObj`, opcional) |
| `CapituloVideo` | `capitulos_video` | titulo, descripcion, tiempoInicio, tiempoFin, ordenCapitulo, origen, **creadoManual**, **generadoPorIa** | N—1 Video |
| `ConceptoClaveVideo` | `conceptos_clave_video` | nombre, definicion, tiempoInicio, **tiempoFin**, ordenConcepto, **creadoManual**, **generadoPorIa** | N—1 Video |
| `FragmentoTranscripcion` | `fragmentos_transcripcion` | texto, tiempoInicio, tiempoFin, ordenFragmento, embedding (pgvector) | N—1 Video |

**Compatibilidad y migración:** `Video.profesor`, `Video.asignatura` y `Asignatura.profesor` se conservan como texto para no perder datos antiguos. Al arrancar, dos inicializadores migran esos textos al modelo relacional sin borrarlos:

- `InicializadorAsignaturas`: crea una `Asignatura` por cada texto de asignatura sin relación y vincula el vídeo.
- `InicializadorProfesores`: crea un `Profesor` por cada texto de profesor (de vídeos y asignaturas) sin relación y lo vincula.

Hibernate (`database.generation=update`) crea automáticamente las tablas y columnas nuevas (`profesores`, `videos.resumen`, `videos.profesor_id`, `asignaturas.profesor_id`, flags de capítulos/conceptos) al reiniciar.

### Probar manualmente profesores, asignaturas, capítulos y conceptos

Con el backend en marcha (`http://localhost:8080`):

```bash
# Crear un profesor
curl -X POST localhost:8080/api/profesores -H 'Content-Type: application/json' \
  -d '{"nombre":"Manuel Martín","email":"manuel@upsa.es"}'

# Crear una asignatura con ese profesor (usa el id devuelto arriba)
curl -X POST localhost:8080/api/asignaturas -H 'Content-Type: application/json' \
  -d '{"nombre":"Ingeniería del Software","descripcion":"Patrones y arquitectura","idProfesor":1}'

# Asignar una clase a esa asignatura y profesor + guardar resumen
curl -X PATCH localhost:8080/api/transcripciones/451/metadata -H 'Content-Type: application/json' \
  -d '{"idAsignatura":1,"idProfesor":1,"resumen":"Clase sobre DTO y capas."}'

# Añadir un capítulo manual a la clase
curl -X POST localhost:8080/api/transcripciones/451/capitulos -H 'Content-Type: application/json' \
  -d '{"titulo":"Introducción","descripcion":"Contexto inicial","tiempoInicio":0,"tiempoFin":90}'

# Editar / borrar ese capítulo (usa el id devuelto)
curl -X PATCH localhost:8080/api/capitulos/1 -H 'Content-Type: application/json' -d '{"titulo":"Intro revisada"}'
curl -X DELETE localhost:8080/api/capitulos/1

# Añadir un concepto manual
curl -X POST localhost:8080/api/transcripciones/451/conceptos -H 'Content-Type: application/json' \
  -d '{"nombre":"DTO","definicion":"Objeto de transferencia de datos","tiempoInicio":15}'
```

Tras cada operación, recargar la clase (`GET /api/transcripciones/451/capitulos` y `/conceptos`) confirma que los datos quedan persistidos en PostgreSQL.

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

### Metadata persistente de clase

La entidad `Video` guarda también información editable por el usuario:

- `asignatura`: permite agrupar la clase dentro de `Mis Cursos`.
- `profesor`: identifica al docente o responsable de la clase.
- `fecha_clase`: fecha académica asignada por el usuario.
- `completado`: indica si la clase se ha visto o se ha marcado como completada.

En desarrollo, Hibernate está configurado con `quarkus.hibernate-orm.database.generation=update`, por lo que añade columnas nuevas al reiniciar el backend. Para vídeos antiguos, si `completado` viene como `NULL`, la API lo normaliza a `false`.

### Flujo del asistente ComprendiA

El chat distingue entre dos tipos de preguntas:

- Preguntas globales: resumen, puntos importantes, qué se consigue o qué muestra el vídeo.
- Preguntas concretas: dudas sobre un concepto, una explicación o un fragmento específico.

Para preguntas globales, el backend construye contexto con título, capítulos, conceptos clave y fragmentos distribuidos de toda la clase. Para preguntas concretas, usa búsqueda semántica sobre fragmentos y responde con las fuentes correspondientes.

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

## Próximas fases y pendientes (para la próxima sesión)

### Pendientes inmediatos / verificación

- **Conexión a la base de datos (Neon):** tras rotar la contraseña, queda **pendiente verificar** que `mvn quarkus:dev` conecta correctamente. El backend acepta `DATABASE_URL` en formato libpq (`postgresql://usuario:password@host/db?sslmode=require`) y la parsea `DatasourceUrlConfigSourceFactory`. ⚠️ Hay una incoherencia con la documentación de arriba (que muestra formato `jdbc:...?user=...&password=...`); conviene unificar el formato real esperado y dejarlo claro en `backend/.env.example`. Revisar el log `[Datasource] ... passLen=N` al arrancar.
- **Validar autoasignación en vivo:** procesar (1) vídeo de canal ya asociado → match por CANAL, (2) canal nuevo con tema parecido → SEMANTICA, (3) vídeo totalmente nuevo → asignatura nueva; y comprobar que el pill muestra "(sugerencia)" y que al cambiar a mano desaparece y persiste.
- **Revisar los logs de tiempos `[Clasificacion][Tiempo]`** para confirmar qué fase domina (sospechas: `yt-dlp` de canal y embeddings de OpenAI) antes de optimizar.

### Optimización de la clasificación (cuando haya medidas)

- Reutilizar la llamada a `yt-dlp` del título para obtener también el canal en una sola invocación (evitar la segunda llamada en `obtenerMetadatosCanal`).
- Cachear/precalcular `embeddingResumen` de las asignaturas (hoy se calcula de forma perezosa la primera vez que se compara) y recalcularlo cuando cambie su nombre/descripción/palabras clave.
- Considerar mover `embeddingResumen` a una columna `pgvector` (como los fragmentos) si el número de asignaturas crece, en lugar de JSON en TEXT.
- Hacer la autoasignación asíncrona / fuera de la transacción del pipeline si añade latencia perceptible al "COMPLETADO".

### Mejoras funcionales pendientes

- **Sugerir también el profesor**: ya se copia el profesor de la asignatura sugerida cuando el vídeo no tiene uno; falta reflejar visualmente que el profesor es "sugerido" (hoy solo lo está la asignatura).
- **Aprendizaje del canal**: afinar la regla de cuándo una asignatura "aprende" un `canalYoutubeId` (al asignar manualmente o por clasificación) para evitar arrastrar canales equivocados.
- **Umbral semántico configurable** (`UMBRAL_SIMILITUD`, hoy fijo en 0.30) vía propiedad de configuración.
- **Chat conversacional**: persistencia opcional del hilo, límite de tokens del contexto, y mejor extracción de entidades para referencias implícitas.
- **Clases relacionadas**: implementar la sección real (clases de la misma asignatura con fechas cercanas), hoy con placeholders.

### Calidad / infra

- **Tests**: la autoasignación está deshabilitada en test (`comprendia.clasificacion.habilitada=false`) para no llamar a yt-dlp/OpenAI. Falta cobertura unitaria de `ClasificacionAsignaturaServicio` (coseno, match por canal, nombre sugerido) con dependencias simuladas.
- **Budget de CSS** de `ng build`: `app.css` supera el presupuesto por defecto (el build de producción falla aunque `ng serve` funciona). Subir el límite en `angular.json` o dividir estilos.

---

## Autor

Iván Zheng
