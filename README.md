# doc-ocr-spring-backend

A Spring Boot backend that accepts a PDF upload and returns OCR-extracted text, using the [OCR.space](https://ocr.space/ocrapi) API as the underlying OCR engine.

## Overview

The service exposes a single REST endpoint that takes a multipart PDF upload, forwards it to the OCR.space API, and returns the extracted text per page.

```
POST /api/ocr/v0/pdf (multipart "file")
  -> validates file present + content-type contains "pdf"
  -> calls OCR.space API
  -> parses response into per-page text
  -> returns { filename, text, status }
```

**Stack:** Java 22, Spring Boot 4.0.1, Gradle (wrapper included), Jackson.

## Prerequisites

- JDK 22
- An OCR.space API key (optional — defaults to the public demo key `helloworld`, which is rate-limited). Get a free key at https://ocr.space/ocrapi.

## Setup

Clone the repo and set your API key as an environment variable (optional):

```bash
export OCR_SPACE_API_KEY=your-api-key-here
```

If unset, the app falls back to OCR.space's demo key (`helloworld`), which has strict rate limits and is fine only for quick testing.

## Running the app

```bash
./gradlew bootRun
```

On Windows:

```bash
gradlew.bat bootRun
```

The app starts on `http://localhost:8080` by default.

## Usage

Upload a PDF for OCR processing:

```bash
curl -X POST http://localhost:8080/api/ocr/v0/pdf \
  -F "file=@/path/to/document.pdf"
```

Example response:

```json
{
  "filename": "document.pdf",
  "text": "page 1 text\n---\npage 2 text",
  "status": "success"
}
```

Error responses:
- `400 Bad Request` — no file provided
- `415 Unsupported Media Type` — file is not a PDF
- `500 Internal Server Error` — OCR processing failed

## Running tests

```bash
./gradlew test
```

## Project structure

See [CLAUDE.md](CLAUDE.md) for a detailed breakdown of the codebase, request flow, and known gaps/limitations.
