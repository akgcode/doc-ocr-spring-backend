# doc-ocr-spring-backend

Spring Boot backend that accepts a PDF upload and returns OCR-extracted text via the OCR.space API. Uploads/results are persisted to PostgreSQL and the endpoint is protected by HTTP Basic auth.

## Stack
- Java 22, Spring Boot 4.0.1, Gradle (wrapper included)
- Jackson for JSON, Lombok available (compileOnly/annotationProcessor, not yet used in code)
- Spring Security (HTTP Basic), Spring Data JPA + PostgreSQL, schema managed by Hibernate (`spring.jpa.hibernate.ddl-auto=update`) — no Flyway; deemed unnecessary process for a non-production app

## Request flow
```
POST /api/ocr/v0/pdf (multipart "file", HTTP Basic auth required)
  -> DocOcrController: validates file present + content-type in ocr.upload.allowed-content-types
       (spring.servlet.multipart.max-file-size=5MB enforced by Spring before this point)
  -> OcrServiceImpl.processPdf(file)
       - saves an OcrJob row (status PENDING) via OcrJobRepository
       - builds multipart request, calls OCR.space API via RestTemplate
       - parses response Map -> OcrSpaceResponse DTO (Jackson convertValue)
       - parsePdf(): walks ParsedResults[], joins ParsedText per page with "\n---\n"
       - updates the OcrJob row to SUCCESS (with extractedText) or FAILED (with errorMessage)
  -> returns OcrResponse{id, filename, text, status}
```

## Key files
- [DocOcrController](src/main/java/com/akg/doc_ocr_spring_backend/controller/DocOcrController.java) — single POST endpoint, allowed-content-type validation via `OcrUploadProperties`, maps IOException to 500
- [GlobalExceptionHandler](src/main/java/com/akg/doc_ocr_spring_backend/controller/GlobalExceptionHandler.java) — `@RestControllerAdvice` mapping `MaxUploadSizeExceededException` to 413
- [OcrService](src/main/java/com/akg/doc_ocr_spring_backend/service/OcrService.java) / [OcrServiceImpl](src/main/java/com/akg/doc_ocr_spring_backend/service/Impl/OcrServiceImpl.java) — calls OCR.space, parses response, persists `OcrJob`; `processPdf` returns the saved `OcrJob` entity
- [OcrSpaceResponse](src/main/java/com/akg/doc_ocr_spring_backend/dto/OcrSpaceResponse.java) — DTO mirroring OCR.space's JSON (PascalCase `@JsonProperty`), including nested `ParsedResult`
- [OcrResponse](src/main/java/com/akg/doc_ocr_spring_backend/dto/OcrResponse.java) — this API's own response shape (id/filename/text/status)
- [OcrJob](src/main/java/com/akg/doc_ocr_spring_backend/entity/OcrJob.java) / [OcrJobRepository](src/main/java/com/akg/doc_ocr_spring_backend/repository/OcrJobRepository.java) — persistence for job/result history (status PENDING/SUCCESS/FAILED); schema is Hibernate-managed (`ddl-auto=update`), no Flyway migrations
- [AppConfig](src/main/java/com/akg/doc_ocr_spring_backend/config/AppConfig.java) — defines the `RestTemplate` bean, enables `OcrUploadProperties`
- [OcrUploadProperties](src/main/java/com/akg/doc_ocr_spring_backend/config/OcrUploadProperties.java) — `ocr.upload.*` config (allowed content types)
- [SecurityConfig](src/main/java/com/akg/doc_ocr_spring_backend/config/SecurityConfig.java) — HTTP Basic auth, all `/api/**` requests require authentication (`/actuator/health` and CORS preflight `OPTIONS` permitted); wires the `CorsConfigurationSource` bean via `http.cors(...)` so preflight is evaluated before auth
- [CorsConfig](src/main/java/com/akg/doc_ocr_spring_backend/config/CorsConfig.java) — permissive CORS for `/api/**` via a `CorsConfigurationSource` bean (not `WebMvcConfigurer`, so Spring Security's filter chain can consume it), for frontend integration during local dev
- [LoggerService](src/main/java/com/akg/doc_ocr_spring_backend/logging/LoggerService.java) — thin wrapper around a single `"app-logger"` SLF4J logger; `.error()` does manual `String.format` when a `Throwable` is passed

## Config
- [application.properties](src/main/resources/application.properties):

  - `ocr.space.api.url`, `ocr.space.api.key` (env var `OCR_SPACE_API_KEY`, defaults to OCR.space's public demo key `helloworld`)
  - `spring.servlet.multipart.max-file-size` / `max-request-size` = 5MB, `ocr.upload.allowed-content-types` = `application/pdf`
  - `spring.security.user.name` / `spring.security.user.password` (env vars `APP_USER` / `APP_PASSWORD`, default `admin`/`admin` — change for anything beyond local dev)
  - `spring.datasource.url` / `username` / `password` (env vars `DB_URL` / `DB_USER` / `DB_PASSWORD`, default to the `docker-compose.yml` Postgres instance on host port 5433 — chosen to avoid clashing with a local Postgres install on 5432)
- [docker-compose.yml](docker-compose.yml) — local PostgreSQL 16 for dev, matches the datasource defaults above

## Notable current gaps / things to know before enhancing

- `OcrService.parsePdf` takes a raw `Map<String,Object>`, not the DTO — it re-converts internally with a locally-`new`'d `ObjectMapper` per call rather than an injected/shared one.
- Auth is a single hardcoded user via properties (`spring.security.user.*`), not a DB-backed user table — fine for one caller, not for multi-user/role scenarios.
- No MIME-sniffing (only trusts the client-supplied `Content-Type` header) — the allowed-types check is still a header comparison, not content inspection.
- `RestTemplate` (not `WebClient`) is used for the outbound call; synchronous, no timeout configured.
- Single test file exists (`DocOcrSpringBackendApplicationTests`), context-load only — no controller/service tests yet.
- CORS is wide open (`allowedOriginPatterns("*")`) — fine for local dev, should be scoped to the real frontend origin before any shared/prod deployment.
- `OcrJob.extractedText` uses `@Column(columnDefinition = "TEXT")`, not `@Lob` — `@Lob` on a `String` maps to Postgres `oid` (large object), which wouldn't match Hibernate's auto-generated `TEXT` column.
- Schema is Hibernate-managed via `spring.jpa.hibernate.ddl-auto=update` — fine for local/non-prod use, but has no migration history or rollback; revisit (e.g. Flyway) before any shared/prod deployment.
- The app's JVM timezone is pinned to UTC in `build.gradle` (`bootRun`/`test` tasks) — without it, some JVM default timezone IDs (e.g. `Asia/Calcutta`, the legacy alias for `Asia/Kolkata`) aren't recognized by the Postgres JDBC driver's startup handshake and the connection is rejected.

## Project General Instructions
- if execution diverges from the approved plan, stop and re-enter Plan Mode rather than improvising.
- After ANY correction from the user: update the plan file
- Update README.md each time you generate a new version.
- Minimize the amount of code generated.
