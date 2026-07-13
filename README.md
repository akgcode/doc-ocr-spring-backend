# doc-ocr-spring-backend

A Spring Boot backend that accepts a PDF upload and returns OCR-extracted text, using the [OCR.space](https://ocr.space/ocrapi) API as the underlying OCR engine. Uploads and results are persisted to PostgreSQL, and the endpoint requires HTTP Basic authentication.

## Overview

The service exposes a single REST endpoint that takes a multipart PDF upload, forwards it to the OCR.space API, persists the job and result, and returns the extracted text per page.

```
POST /api/ocr/v0/pdf (multipart "file", HTTP Basic auth required)
  -> validates file present + content-type is an allowed type + within max upload size
  -> persists an OcrJob row (PENDING)
  -> calls OCR.space API
  -> parses response into per-page text, updates OcrJob (SUCCESS/FAILED)
  -> returns { id, filename, text, status }
```

**Stack:** Java 22, Spring Boot 4.0.1, Gradle (wrapper included), Jackson, Spring Security, Spring Data JPA, PostgreSQL (schema auto-managed by Hibernate, no migration tool).

## Prerequisites

- JDK 22
- Docker (for local PostgreSQL via `docker-compose.yml`)
- An OCR.space API key (optional — defaults to the public demo key `helloworld`, which is rate-limited). Get a free key at https://ocr.space/ocrapi.

## Setup

Clone the repo and set the following environment variables as needed:

```bash
export OCR_SPACE_API_KEY=your-api-key-here   # optional, defaults to the "helloworld" demo key
export APP_USER=admin                        # optional, defaults to "admin"
export APP_PASSWORD=your-basic-auth-password # optional, defaults to "admin" - change for anything beyond local dev
export DB_URL=jdbc:postgresql://localhost:5433/doc_ocr  # optional, matches docker-compose default (host port 5433 to avoid clashing with a local Postgres install on 5432)
export DB_USER=doc_ocr                       # optional
export DB_PASSWORD=doc_ocr                   # optional
```

Start PostgreSQL locally:

```bash
docker compose up -d
```

## Running the app

```bash
./gradlew bootRun
```

On Windows:

```bash
gradlew.bat bootRun
```

The app starts on `http://localhost:8080` by default. Hibernate auto-creates/updates the database schema on startup (`spring.jpa.hibernate.ddl-auto=update`).

## Usage

Upload a PDF for OCR processing (HTTP Basic auth required):

```bash
curl -X POST http://localhost:8080/api/ocr/v0/pdf \
  -u admin:admin \
  -F "file=@/path/to/document.pdf"
```

Example response:

```json
{
  "id": 1,
  "filename": "document.pdf",
  "text": "page 1 text\n---\npage 2 text",
  "status": "success"
}
```

Error responses:
- `400 Bad Request` — no file provided
- `401 Unauthorized` — missing/invalid credentials
- `413 Payload Too Large` — file exceeds the 5MB limit
- `415 Unsupported Media Type` — file content-type is not in the allowed list (`ocr.upload.allowed-content-types`, defaults to `application/pdf`)
- `500 Internal Server Error` — OCR processing failed

### Frontend integration notes

The endpoint accepts a standard `multipart/form-data` POST, so any frontend can call it with `fetch`/`FormData` or `axios`. CORS is enabled for `/api/**` for local development. Since auth is HTTP Basic, the frontend must send an `Authorization: Basic <base64(user:pass)>` header on the request — fine for a first integration, but a token-based scheme (e.g. JWT) is recommended once there's a real login flow.

## Running tests

```bash
./gradlew test
```

## Project structure

See [CLAUDE.md](CLAUDE.md) for a detailed breakdown of the codebase, request flow, and known gaps/limitations.

## Future scope

- **Flyway migrations**: schema is currently Hibernate-managed (`spring.jpa.hibernate.ddl-auto=update`), which is fine for local/single-developer use but has no versioned migration history or rollback path. If this app moves toward a shared or production environment, reintroduce Flyway (`spring-boot-starter-flyway` + `flyway-database-postgresql`, switch `ddl-auto` to `validate`) to get repeatable, auditable schema changes across environments.
