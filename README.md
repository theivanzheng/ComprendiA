# ComprendiA

Aplicación educativa inteligente para analizar clases grabadas.

## Stack

- **Backend**: Java 21 + Quarkus
- **Frontend**: Angular
- **IA/RAG (futuro)**: LangChain4j
- **Base vectorial (futura)**: Qdrant o PostgreSQL + pgvector

## Estructura del proyecto

```
Comprendia/
├── backend/    # API REST con Quarkus
├── frontend/   # Interfaz con Angular
├── docs/       # Documentación del proyecto
└── README.md
```

## Ejecutar el backend

```bash
cd backend
mvn quarkus:dev
```

El servidor arranca en `http://localhost:8080`.

Endpoint disponible:

```
GET http://localhost:8080/api/salud
```

Respuesta esperada:

```json
{"estado":"activo","servicio":"ComprendiA"}
```

## Ejecutar el frontend

```bash
cd frontend
npm install
ng serve
```

La aplicación arranca en `http://localhost:4200`.

## Estado actual

- [x] Endpoint `/api/salud`
- [ ] Integración con YouTube
- [ ] Transcripción automática
- [ ] Motor RAG
