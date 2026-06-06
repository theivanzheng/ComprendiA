# 003 — Fragmentos demasiado pequeños (mala recuperación)

- **Fecha:** 2026-06-05
- **Componente:** pipeline de procesamiento
- **Archivos:** `backend/.../servicio/ReagrupadorFragmentosServicio.java` (nuevo), `backend/.../servicio/TranscripcionYoutubeServicio.java`, `backend/.../dto/RespuestaTranscripcionDTO.java`
- **Commit del arreglo:** `2a85288`

## Síntoma

El chat no encontraba bien la información del vídeo. En "Momentos relacionados" aparecían rangos de
**1–2 segundos** (p. ej. "1:50–1:51").

## Diagnóstico

Los subtítulos de YouTube (y a veces Whisper) llegan troceados en fragmentos muy cortos. Un
fragmento de 1 segundo es media frase: su *embedding* apenas tiene significado, así que la búsqueda
semántica casa mal.

## Causa raíz

**Granularidad de troceado demasiado fina.** Se generaba un *embedding* por cada microfragmento, lo
que daba vectores pobres y una recuperación de baja calidad.

## Corrección

Se creó `ReagrupadorFragmentosServicio`, una etapa de **preprocesamiento** en el pipeline que
fusiona fragmentos consecutivos en **ventanas de ~25 segundos** (o 600 caracteres), conservando las
marcas de tiempo (inicio del primero, fin del último) para no perder la trazabilidad al minuto.

Algoritmo: recorrido en **una sola pasada** (O(n)) con un acumulador (`StringBuilder`) y volcado por
umbral (duración o longitud). Se hace **a medida** —y no con un troceador genérico— precisamente
para preservar los *timestamps*. Se conecta en `TranscripcionYoutubeServicio` justo antes de
persistir y generar *embeddings*.

## Verificación

Al procesar un vídeo nuevo, en los logs: `[Troceado] Reagrupados 105 fragmentos pequeños en 18
fragmentos de ~25s`. Los "Momentos relacionados" pasan a ser tramos de ~25 s.

## Concepto aprendido

En RAG, el **tamaño del *chunk*** es un parámetro de diseño crítico: trozos demasiado pequeños dan
*embeddings* pobres; demasiado grandes diluyen el contexto. ~20–30 s es un buen compromiso para
transcripciones de vídeo. Solo afecta a vídeos procesados **después** del cambio.
