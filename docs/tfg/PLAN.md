# Plan de documentación del TFG — ComprendiA

Documento de trabajo para preparar la memoria del TFG. **Hoy solo planificamos**; la
redacción empieza en la próxima sesión.

## Decisiones tomadas

- **Formato de entrega:** LaTeX (escribiremos `.tex`, no Markdown→export).
- **Idioma:** español, registro académico formal.
- **Dónde trabajamos:** en este mismo repositorio, para poder citar el código real
  (arquitectura, pipeline, modelo de datos, endpoints) en vez de reconstruirlo de memoria.
- **Carpeta de documentación:** `docs/tfg/`.

## Cómo usaremos los PDF

1. Tú dejas **todos los PDF** en `docs/tfg/pdfs/`:
   - Normas de estilo/entrega de la facultad.
   - Plantilla o documento de estructura obligatoria.
   - El TFG de ejemplo (~60 págs) de tu compañero.
2. **No hace falta convertirlos a `.md` a mano**: yo los leo directamente (incluido el PDF largo).
3. A partir de ellos generaré una sola vez:
   - `guia-normas.md` → reglas extraídas (márgenes, tipografía, formato de citas/bibliografía,
     numeración, portada, longitud, secciones obligatorias…).
   - `estructura-tfg.md` → esqueleto de capítulos validado contra las normas reales.
4. El TFG de ejemplo se usa **solo como referencia** de tono, longitud y orden. ⚠️ No se copia
   (riesgo de plagio); adaptamos la estructura a este proyecto.

## Estructura PROVISIONAL de capítulos

> Borrador típico de un TFG de ingeniería del software. **Pendiente de ajustar** a las normas
> de la facultad cuando lea los PDF.

1. **Introducción** — contexto, motivación, problema que resuelve ComprendiA, objetivos
   (generales y específicos), alcance, estructura de la memoria.
2. **Estado del arte / Marco teórico** — transcripción automática (Whisper, subtítulos),
   embeddings y búsqueda semántica, RAG, herramientas educativas similares.
3. **Análisis de requisitos** — funcionales y no funcionales, casos de uso, actores.
4. **Tecnologías y herramientas** — Java 21/Quarkus, Angular (zoneless), PostgreSQL/Neon +
   pgvector, OpenAI (Whisper, embeddings, gpt-4o-mini), yt-dlp.
5. **Diseño** — arquitectura general (frontend/backend), modelo de datos, diseño de la API,
   pipeline de procesamiento asíncrono, diseño de la clasificación automática de asignaturas.
6. **Implementación** — decisiones clave por módulo: pipeline de transcripción, RAG/chat
   conversacional, análisis de clase (capítulos/conceptos), autoasignación de asignatura,
   gestión de cursos. (Aquí citamos código real del repo.)
7. **Pruebas y validación** — tests automáticos, validación funcional, métricas/tiempos.
8. **Conclusiones y líneas futuras** — objetivos cumplidos, limitaciones, trabajo futuro
   (ver sección "Próximas fases" del README).
9. **Bibliografía** y **Anexos** (manual de uso, capturas, etc.).

## Material del repo ya aprovechable como fuente

- `README.md` — visión funcional, endpoints, modelo de datos, arquitectura del pipeline,
  sección "Próximas fases y pendientes".
- `docs/arquitectura.md` — (ya existe en el repo; revisar y reutilizar).
- Código backend (`backend/src/main/java/es/comprendia/...`) y frontend (`frontend/src/app/...`).

## Siguiente sesión — primeros pasos

1. Confirmar que los PDF están en `docs/tfg/pdfs/`.
2. Yo leo normas + plantilla → genero `guia-normas.md` y ajusto `estructura-tfg.md`.
3. Decidir plantilla LaTeX (¿la facultad da una? ¿usamos una base tipo `report`/`book` o
   una plantilla de TFG concreta?) y montar el esqueleto `.tex` con capítulos vacíos.
4. Empezar a redactar por el capítulo que prefieras (sugerencia: Diseño/Implementación,
   que es donde el código ya nos da mucho material).

## Cosas que necesito de ti para arrancar

- Los PDF en la carpeta.
- ¿La facultad proporciona **plantilla LaTeX** oficial? (si sí, ponla también en `pdfs/` o en
  `docs/tfg/plantilla/`).
- Límite de páginas / fecha de entrega, si los hay.
