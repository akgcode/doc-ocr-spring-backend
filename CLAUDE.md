# doc-ocr-spring-backend

Spring Boot backend that accepts a PDF upload and returns OCR-extracted text via the OCR.space API.

## Stack
- Java 22, Spring Boot 4.0.1, Gradle (wrapper included)
- Jackson for JSON, Lombok available (compileOnly/annotationProcessor, not yet used in code)
- No database / JPA yet — despite the `jpa-patterns` skill being present, there are no entities or repositories in this codebase today.

## Request flow
```
POST /api/ocr/v0/pdf (multipart "file")
  -> DocOcrController: validates file present + content-type contains "pdf"
  -> OcrServiceImpl.processPdf(file)
       - builds multipart request, calls OCR.space API via RestTemplate
       - parses response Map -> OcrSpaceResponse DTO (Jackson convertValue)
       - parsePdf(): walks ParsedResults[], joins ParsedText per page with "\n---\n"
  -> returns OcrResponse{filename, text, status}
```

## Key files
- [DocOcrController](src/main/java/com/akg/doc_ocr_spring_backend/controller/DocOcrController.java) — single POST endpoint, PDF-only validation, maps IOException to 500
- [OcrService](src/main/java/com/akg/doc_ocr_spring_backend/service/OcrService.java) / [OcrServiceImpl](src/main/java/com/akg/doc_ocr_spring_backend/service/Impl/OcrServiceImpl.java) — calls OCR.space, parses response
- [OcrSpaceResponse](src/main/java/com/akg/doc_ocr_spring_backend/dto/OcrSpaceResponse.java) — DTO mirroring OCR.space's JSON (PascalCase `@JsonProperty`), including nested `ParsedResult`
- [OcrResponse](src/main/java/com/akg/doc_ocr_spring_backend/dto/OcrResponse.java) — this API's own response shape (filename/text/status)
- [AppConfig](src/main/java/com/akg/doc_ocr_spring_backend/config/AppConfig.java) — defines the `RestTemplate` bean
- [LoggerService](src/main/java/com/akg/doc_ocr_spring_backend/logging/LoggerService.java) — thin wrapper around a single `"app-logger"` SLF4J logger; `.error()` does manual `String.format` when a `Throwable` is passed

## Config
- [application.properties](src/main/resources/application.properties): `ocr.space.api.url`, `ocr.space.api.key` (env var `OCR_SPACE_API_KEY`, defaults to OCR.space's public demo key `helloworld`)

## Notable current gaps / things to know before enhancing
- `OcrService.parsePdf` takes a raw `Map<String,Object>`, not the DTO — it re-converts internally with a locally-`new`'d `ObjectMapper` per call rather than an injected/shared one.
- No request size limits, no MIME-sniffing (only trusts the client-supplied `Content-Type` header), no auth on the endpoint.
- `RestTemplate` (not `WebClient`) is used for the outbound call; synchronous, no timeout configured.
- Single test file exists (`DocOcrSpringBackendApplicationTests`), context-load only — no controller/service tests yet.
- No persistence layer at all currently.

## Project General Instructions
- if execution diverges from the approved plan, stop and re-enter Plan Mode rather than improvising.
- After ANY correction from the user: update the plan file
- Update README.md each time you generate a new version.
- Minimize the amount of code generated.