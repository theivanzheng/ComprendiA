# Arquitectura de ComprendiA

## Componentes principales

### Backend (Quarkus)
- Puerto: 8080
- Paquetes:
  - `es.comprendia.recurso` — endpoints REST
  - `es.comprendia.servicio` — lógica de negocio (pendiente)

### Frontend (Angular)
- Puerto: 4200
- Comunica con el backend vía HTTP

## Flujo futuro

1. Usuario introduce URL de YouTube
2. Backend descarga y transcribe el audio
3. Transcripción se indexa en base vectorial
4. Usuario hace preguntas; el sistema responde con RAG
