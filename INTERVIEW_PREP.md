# Backend Engineer Interview Prep — grounded in `doc-ocr-spring-backend`

**Target:** Software Engineer (Backend), ~3 years experience · Java / Spring Boot
**How this doc is built:** every answer is anchored to a real file, annotation, or decision in this repository — not generic textbook Spring trivia. Where a question probes a gap this project actually has (no tests, plaintext password, blocking HTTP client, etc.), the answer says so honestly, because "what would you improve?" is one of the most common follow-ups in a 3-YOE interview.

**Stack recap:** Java 22 · Spring Boot 4.0.1 · Gradle · Spring Web (MVC) · Spring Security (HTTP Basic) · Spring Data JPA + Hibernate · PostgreSQL 16 (Docker Compose) · Jackson · SLF4J/Logback · RestTemplate → OCR.space (external API)

Each question has a direct answer, and most have a collapsible **Follow-ups** block — expand those, interviewers rarely stop at the first answer.

---

## Table of contents
1. [Core Java](#1-core-java)
2. [Spring IoC & Dependency Injection](#2-spring-ioc--dependency-injection)
3. [Spring Boot fundamentals](#3-spring-boot-fundamentals)
4. [Spring MVC / REST layer](#4-spring-mvc--rest-layer)
5. [Spring Security](#5-spring-security)
6. [Spring Data JPA & Hibernate](#6-spring-data-jpa--hibernate)
7. [Schema management: Hibernate `ddl-auto` vs Flyway](#7-schema-management-hibernate-ddl-auto-vs-flyway)
8. [PostgreSQL & transactions](#8-postgresql--transactions)
9. [Exception handling](#9-exception-handling)
10. [Externalized configuration](#10-externalized-configuration)
11. [CORS](#11-cors)
12. [Design patterns actually used here](#12-design-patterns-actually-used-here)
13. [HTTP clients: RestTemplate, WebClient, RestClient](#13-http-clients-resttemplate-webclient-restclient)
14. [Jackson & JSON mapping](#14-jackson--json-mapping)
15. [Logging](#15-logging)
16. [Testing (and this repo's gap)](#16-testing-and-this-repos-gap)
17. [File upload handling & security](#17-file-upload-handling--security)
18. [System design extensions](#18-system-design-extensions)
19. [Recent trends (2025–2026 interview radar)](#19-recent-trends-20252026-interview-radar)
20. [Project deep-dive / behavioral](#20-project-deep-dive--behavioral)

---

## 1. Core Java

**Q: This service defines `OcrService` as an interface with `OcrServiceImpl` as the sole implementation. Why bother with an interface for something with one implementation?**
A: It decouples the contract from the implementation so the controller depends on an abstraction (`OcrService`), not a concrete class — Spring injects whichever bean satisfies the interface. Practically it enables mocking in unit tests (`@MockBean OcrService`) without spinning up `RestTemplate`/JPA, and it leaves room for a second implementation (e.g., a different OCR provider) without touching the controller.

<details>
<summary>Follow-ups</summary>

- If there's genuinely only ever going to be one implementation forever, is the interface still worth it? (Reasonable answer: it's a convention many teams enforce for testability even with one impl — but it's a judgment call, not a law.)
- What's the difference between programming to an interface and the Dependency Inversion Principle (the "D" in SOLID)?
- How would Spring resolve ambiguity if two beans implemented `OcrService`? (`@Primary`, `@Qualifier`, or bean name matching the field/parameter name.)
</details>

**Q: `OcrJob.Status` is a Java `enum` with three constants (`PENDING`, `SUCCESS`, `FAILED`), persisted via `@Enumerated(EnumType.STRING)`. Why prefer `STRING` over the default `ORDINAL`?**
A: `ORDINAL` stores the enum's declaration index (0, 1, 2…) as an integer. If someone later inserts a new constant in the middle of the enum, every previously stored ordinal now points at the wrong meaning — a silent data-corruption bug. `STRING` stores the constant's name as text, so it's self-describing and safe to reorder or insert new constants (only renaming a constant is unsafe).

<details>
<summary>Follow-ups</summary>

- What's the storage/performance trade-off of `STRING` vs `ORDINAL`? (Slightly larger column, marginally slower comparisons — irrelevant at this scale, worth it for safety.)
- How would you enforce only these three values at the database level? (This project's Hibernate `ddl-auto=update` auto-generated a `CHECK` constraint on `status`; you could also hand-write one, or use a Postgres native `ENUM` type.)
- Are Java enums singletons? How does that affect `==` vs `.equals()` comparison? (Yes — each constant is a single JVM instance, so `==` is safe and idiomatic for enum comparison.)
</details>

**Q: `LoggerService.error()` takes `(String message, Throwable t, Object... args)` and does `String.format(message, args)` when `t != null`, but calls `logger.error(message, args)` (SLF4J's own `{}`-placeholder substitution) when `t == null`. Is that a good design?**
A: It's a **latent bug**, not a good design — the method silently switches placeholder syntax depending on whether a `Throwable` is passed. `String.format` expects `%s`/`%d`, while SLF4J expects `{}`. Every call site in this codebase happens to use `{}` (SLF4J style) but `error()` is called with a `Throwable`, so `String.format` runs against `{}` placeholders and does nothing — the args are silently dropped from the formatted string (though SLF4J still logs the stack trace). This is a good "spot the bug" question for a code-review round.

<details>
<summary>Follow-ups</summary>

- How would you fix it? (Use `org.slf4j.helpers.MessageFormatter.arrayFormat(message, args)` to resolve `{}` placeholders yourself before passing the final string + throwable to `logger.error(String, Throwable)`.)
- Why does varargs (`Object... args`) make this kind of bug easy to introduce and hard to catch at compile time? (No compiler check ties format-string placeholder count/type to the args array — it's a purely runtime contract, same class of issue as `String.format` mismatches or `printf`.)
</details>

**Q: `OcrResponse` has three constructors — a no-arg, a 3-arg `(filename, text, status)`, and a 4-arg `(id, filename, text, status)`. Why not just use one constructor with nullable `id`, or a `Builder`?**
A: The 3-arg constructor exists because error responses (bad request, unsupported type, processing error) don't have a job `id` yet — the job hasn't necessarily been persisted, or persistence failed. Telescoping constructors like this work at small scale but get unreadable fast; the idiomatic fix here would be either a `Builder` pattern, static factory methods (`OcrResponse.error(...)`, `OcrResponse.success(...)`), or (given Java 22) a `record` with a compact canonical constructor plus overloaded static factories.

<details>
<summary>Follow-ups</summary>

- Why might you choose a Java `record` over a plain class for a DTO like this in 2024+? (Immutability by default, auto-generated `equals`/`hashCode`/`toString`, less boilerplate — but Jackson needs either the default constructor+setters this class already has, or `@JsonCreator`/canonical-constructor binding, which works out of the box for records in modern Jackson.)
- What's the telescoping constructor anti-pattern, and how does the Builder pattern solve it?
</details>

---

## 2. Spring IoC & Dependency Injection

**Q: `DocOcrController` takes `OcrService`, `LoggerService`, and `OcrUploadProperties` as constructor parameters with `@Autowired` on the constructor. Is `@Autowired` even necessary here?**
A: No — since Spring 4.3, if a class has exactly **one** constructor, `@Autowired` is optional; Spring uses it implicitly for dependency injection. It's present here either as an explicit style choice or a holdover habit. It becomes *required* only if the class has multiple constructors and you need to tell Spring which one to use for injection.

<details>
<summary>Follow-ups</summary>

- Why is constructor injection preferred over field injection (`@Autowired private OcrService ocrService;`)? (Enables `final` fields → immutability; makes required dependencies explicit and impossible to construct in an invalid state; trivially testable without reflection or a Spring context; fails fast at startup instead of with an NPE at first use.)
- What's a circular dependency, and how does Spring handle it with constructor injection vs field injection? (Constructor injection fails fast at context startup with a `BeanCurrentlyInCreationException`; field/setter injection can sometimes paper over circularity via early bean references, which usually just delays the pain.)
- What's the default Spring bean scope? (`singleton` — one instance per `ApplicationContext`, shared across all injection points. Contrast with `prototype`, `request`, `session`.)
</details>

**Q: `AppConfig` is annotated `@Configuration` and declares a `RestTemplate` bean via `@Bean`. What's the difference between `@Component`/`@Service` and a `@Bean` method inside `@Configuration`?**
A: `@Component` (and its specializations `@Service`, `@Repository`, `@Controller`) are **class-level** stereotypes — Spring finds and instantiates the annotated class itself via classpath scanning. A `@Bean` method is used when you need to register an instance of a class you **don't own** (like `RestTemplate`, a third-party class you can't annotate) or when object creation needs custom logic beyond a no-arg constructor.

<details>
<summary>Follow-ups</summary>

- Why is `RestTemplate` registered as a bean instead of just doing `new RestTemplate()` inline in `OcrServiceImpl`? (Testability — you can inject a mock; reuse — one shared instance/connection pool config instead of one per class; centralizes configuration like timeouts, interceptors, message converters in one place.)
- What does `@EnableConfigurationProperties(OcrUploadProperties.class)` on `AppConfig` do, and why is it needed alongside `@ConfigurationProperties` on `OcrUploadProperties` itself? (`@ConfigurationProperties` alone just marks the class as a properties-binding target; it still needs to be registered as a bean somehow — either via component scanning if you add `@Component` to it, or explicitly via `@EnableConfigurationProperties`, which is the more common/idiomatic pattern for library-style config classes.)
- What are `@Service` and `@Repository` for, semantically, if they behave almost identically to `@Component`? (`@Repository` additionally enables Spring's persistence-exception translation, converting JDBC/Hibernate-specific exceptions into Spring's unchecked `DataAccessException` hierarchy. `@Service` is purely semantic/documentation — marks business-logic beans.)
</details>

---

## 3. Spring Boot fundamentals

**Q: What does `@SpringBootApplication` actually do?**
A: It's a meta-annotation bundling three: `@Configuration` (this class can define beans), `@EnableAutoConfiguration` (triggers Spring Boot's classpath-based auto-configuration — e.g., seeing `spring-boot-starter-web` on the classpath auto-configures an embedded Tomcat and `DispatcherServlet`), and `@ComponentScan` (scans the current package and sub-packages for `@Component`/`@Service`/`@Repository`/`@Controller` beans).

<details>
<summary>Follow-ups</summary>

- How does auto-configuration decide *whether* to configure something? (`@Conditional` family — `@ConditionalOnClass`, `@ConditionalOnMissingBean`, `@ConditionalOnProperty` — auto-config classes back off if you've already defined your own bean of that type, or if a required class isn't on the classpath.)
- What's a "starter" (e.g., `spring-boot-starter-security`)? (A curated dependency-descriptor POM/Gradle module with no code of its own — just a bundle of compatible transitive dependencies plus, often, an auto-configuration module, so you don't hand-pick versions.)
- This project is on Spring Boot 4.0.1 — what changed from Boot 3.x that bit this project specifically? (Flyway autoconfiguration moved out of the monolithic `spring-boot-autoconfigure` jar into a dedicated `spring-boot-flyway` module, only pulled in via the `spring-boot-starter-flyway` starter — `flyway-core` alone on the classpath, sufficient in 3.x, silently does nothing in 4.x. Great real "I hit this and diagnosed it" story — see [§20](#20-project-deep-dive--behavioral).)
</details>

**Q: `application.properties` uses `${APP_USER:admin}` syntax in several places. What is that?**
A: Spring's property placeholder syntax with a default value: `${ENV_VAR_NAME:default}`. At startup Spring resolves `APP_USER` from the environment (OS env var, `-D` system property, or another property source per Spring's `PropertySource` precedence order); if unset, it falls back to `admin`. This is how the app stays configurable per-environment without code changes.

<details>
<summary>Follow-ups</summary>

- What's the full property source precedence order in Spring Boot? (Roughly, highest to lowest: command-line args → `SPRING_APPLICATION_JSON` → servlet init params → OS env vars → `application-{profile}.properties` → `application.properties` → `@PropertySource` → defaults set in code.)
- What are Spring profiles, and how would you use them here to separate local/dev/prod config? (`application-dev.properties`, `application-prod.properties`, activated via `spring.profiles.active=dev`; this project currently has none — everything is one flat `application.properties` with env-var overrides, which works for a single-environment app but wouldn't scale to genuinely different dev/staging/prod topologies.)
- What is Spring Boot Actuator, and what does `/actuator/health` (explicitly permitted in `SecurityConfig`) give you out of the box? (A production-readiness endpoint exposing liveness/readiness-style health checks — here it aggregates the DB connection health via the datasource auto-configuration. Actuator also exposes `/actuator/metrics`, `/actuator/env`, etc., which you'd normally lock down separately in a real deployment.)
</details>

---

## 4. Spring MVC / REST layer

**Q: Walk through what happens, annotation by annotation, when a POST request hits `/api/ocr/v0/pdf`.**
A: `@RestController` marks the class so return values are serialized straight to the HTTP response body (JSON, via Jackson) instead of resolved as view names — it's shorthand for `@Controller` + `@ResponseBody` on every method. `@RequestMapping("/api/ocr/v0/pdf")` at the class level sets the base path; `@PostMapping(consumes = MULTIPART_FORM_DATA_VALUE)` narrows the method to POST requests whose `Content-Type` is `multipart/form-data`. `@RequestParam("file") MultipartFile file` pulls the uploaded file part named `"file"` out of the multipart body. The method returns `ResponseEntity<OcrResponse>`, giving explicit control over both the HTTP status code and the body — as opposed to just returning `OcrResponse` and always getting `200`.

<details>
<summary>Follow-ups</summary>

- `@RequestParam` vs `@PathVariable` vs `@RequestBody` — when does each apply? (`@RequestParam`: query string or form/multipart fields; `@PathVariable`: segments in the URL template like `/jobs/{id}`; `@RequestBody`: deserializes the entire request body, typically JSON, into an object via `HttpMessageConverter`.)
- Why can't a JSON `@RequestBody` and a file upload (`multipart/form-data`) coexist cleanly in one endpoint? (Different content types entirely — a multipart request is a different wire format from a JSON body; you'd send structured metadata either as another form field parsed manually, or as a JSON string field within the multipart body deserialized separately.)
- This endpoint has no versioning header or content-negotiation, just a literal `v0` in the URL path. What are the alternatives, and what are their trade-offs? (URL path versioning — simple, visible, cache-friendly, used here; header versioning e.g. `Accept: application/vnd.api.v1+json` — cleaner URLs but harder to test/debug/curl by hand; query param versioning — rare, least favored.)
- Why does the controller return `ResponseEntity<OcrResponse>` instead of throwing exceptions for the 400/415 cases and letting a `@ControllerAdvice` handle them uniformly, the way it does for `MaxUploadSizeExceededException`? (Inconsistency worth flagging in review — the 400/415 paths are handled inline with manual status codes, while the 413 path is centralized in `GlobalExceptionHandler`. A cleaner design would throw typed exceptions — e.g., `MissingFileException`, `UnsupportedFileTypeException` — and handle all error mapping in one place.)
</details>

---

## 5. Spring Security

**Q: `SecurityConfig` builds a `SecurityFilterChain` bean with `.csrf(csrf -> csrf.disable())`, `.authorizeHttpRequests(...)`, and `.httpBasic(withDefaults -> {})`. Explain each piece, and why CSRF is disabled.**
A: This is the modern (Boot 3+) lambda-DSL replacement for the old `WebSecurityConfigurerAdapter` subclassing style. `authorizeHttpRequests` declares authorization rules — here, `/actuator/health` is `permitAll()` and everything else needs authentication. `.httpBasic()` enables the HTTP Basic auth scheme (credentials sent as a base64-encoded `Authorization: Basic <user:pass>` header on every request). CSRF protection is disabled because CSRF attacks exploit **cookie-based, browser-managed session auth** — a malicious page can make the browser auto-attach a valid session cookie to a forged request. This API is stateless and uses HTTP Basic (credentials must be explicitly supplied by the caller on every request, not auto-attached by the browser), so there's no ambient credential for CSRF to hijack.

<details>
<summary>Follow-ups</summary>

- Is the single configured user (`spring.security.user.name`/`password`) production-appropriate? (No — the password is a plaintext property value, not hashed. Spring Security's `InMemoryUserDetailsManager` — which backs this default single-user setup — does expect a `{noop}`-prefixed or already-encoded password; if it's being accepted as-is, it's effectively unencoded. A real system needs a `PasswordEncoder` bean (`BCryptPasswordEncoder`) and a DB-backed `UserDetailsService`.)
- Why is HTTP Basic considered weak for browser-based frontends specifically? (No logout mechanism — browsers cache Basic credentials until closed; no expiry; credentials resent on *every* request, increasing exposure window; must be paired with HTTPS or credentials go over the wire in near-plaintext base64.)
- What would you migrate to instead, and why? (Token-based auth — JWT or opaque session tokens issued after a real login endpoint, sent as a Bearer token, with expiry/refresh — the natural next step this project's own README flags.)
- What's the order of matchers in `authorizeHttpRequests` — does it matter? (Yes, first-match-wins, same as Spring Security's classic `HttpSecurity` XML config — more specific matchers must precede more general ones like `anyRequest()`.)
- What's the actual filter chain doing under the hood? (A chain of `Filter` — `UsernamePasswordAuthenticationFilter`-family filters for Basic, `ExceptionTranslationFilter`, `FilterSecurityInterceptor`/`AuthorizationFilter` — sitting in front of the `DispatcherServlet`, intercepting every request before it reaches `@RestController` methods. Classic Chain-of-Responsibility pattern — see [§12](#12-design-patterns-actually-used-here).)
</details>

---

## 6. Spring Data JPA & Hibernate

**Q: `OcrJobRepository` is just `interface OcrJobRepository extends JpaRepository<OcrJob, Long> {}` with zero methods written. How does `save()`, `findById()`, etc. actually work with no implementation?**
A: Spring Data JPA generates a runtime **dynamic proxy** implementing the interface (`SimpleJpaRepository` under the hood, wired via `JpaRepositoryFactoryBean`). Method calls on that proxy translate into `EntityManager` operations — `save()` calls `persist()`/`merge()` depending on whether the entity's `@Id` is null, `findById()` becomes `entityManager.find()`, etc. This is the Repository design pattern implemented via dynamic proxies (see [§12](#12-design-patterns-actually-used-here)).

<details>
<summary>Follow-ups</summary>

- How would you add a custom finder, e.g., "find all jobs with status FAILED"? (Just declare `List<OcrJob> findByStatus(OcrJob.Status status);` — Spring Data parses the method name and derives the JPQL query automatically. No implementation needed.)
- What's the difference between `save()` calling `persist()` vs `merge()`? (`persist()` for new/transient entities without an assigned identifier — makes them managed; `merge()` for detached entities that may already exist — copies state onto a managed instance, potentially issuing a `SELECT` first if not already in the persistence context.)
- What is `@Transactional` doing when Spring Data JPA methods are called without you writing it explicitly? (`SimpleJpaRepository`'s methods are internally annotated `@Transactional`, so each repository call gets its own transaction by default if none is already active — which is exactly the trap in `OcrServiceImpl.processPdf` below.)
</details>

**Q: In `OcrServiceImpl.processPdf`, the code calls `ocrJobRepository.save(job)` once with status `PENDING`, then later calls `save(job)` again with status `SUCCESS`/`FAILED` — and the method has no `@Transactional` annotation. What are the implications?**
A: Each `save()` call is its own independent transaction (per the default `@Transactional` on `SimpleJpaRepository` methods). This is actually **intentional and correct** for this use case: if the app crashes or the OCR.space call hangs between the two saves, the `PENDING` row is already durably committed — it survives as an audit trail instead of being rolled back with the rest of the operation. The trade-off is you can end up with jobs permanently stuck in `PENDING` if the process dies mid-flight, with nothing to detect or retry them.

<details>
<summary>Follow-ups</summary>

- If you *did* wrap the whole method in `@Transactional`, what would change? (Both saves would be one atomic unit — a crash mid-call would roll back the `PENDING` insert too, losing the audit trail of the attempt ever happening. Also, since the outbound `RestTemplate` HTTP call happens *inside* the transaction, the DB connection/transaction would be held open for the full duration of a slow external API call — bad for connection pool exhaustion under load.)
- How would you detect and recover stuck `PENDING` jobs in production? (A scheduled job — `@Scheduled` — that queries `findByStatusAndCreatedAtBefore(PENDING, now.minus(threshold))` and either retries or marks them `FAILED` with a timeout reason.)
- What's the "persistence context" / first-level cache, and does it matter across these two separate transactions? (Each transaction gets its own `EntityManager`/persistence context by default — the `job` Java object is detached between the two `save()` calls, which is exactly why the second `save()` needs to be a `merge()`-style call rather than relying on same-transaction dirty checking.)
</details>

**Q: `OcrJob.extractedText` is annotated `@Column(name = "extracted_text", columnDefinition = "TEXT")`, explicitly *not* `@Lob`. Why does that distinction matter?**
A: `@Lob` on a `String` field tells Hibernate to map it to a large-object type — on PostgreSQL that's `oid`, a reference to a separate large-object storage mechanism, not a `text` column at all. That mismatched Hibernate's auto-generated schema expectations against a `text` column and broke schema validation in earlier iterations of this project. Plain `TEXT` in Postgres has no meaningful length limit for this use case, so `@Lob` bought nothing and cost a type mismatch — removing it and using `columnDefinition = "TEXT"` explicitly was the fix.

<details>
<summary>Follow-ups</summary>

- When *would* `@Lob` be the right call? (When you actually want binary/large-object semantics — e.g., mapping a `byte[]` to `bytea`, or when streaming very large content in a database that doesn't have a native large-`TEXT` type.)
- What's the max size of a Postgres `TEXT` column? (~1 GB, functionally unlimited for extracted OCR text.)
</details>

---

## 7. Schema management: Hibernate `ddl-auto` vs Flyway

**Q: This app currently uses `spring.jpa.hibernate.ddl-auto=update` — walk through what each value of `ddl-auto` does, and why `update` was chosen here over `validate` + Flyway.**
A: `none` — Hibernate touches nothing (production default, schema managed externally). `validate` — Hibernate checks the entity mappings against the existing schema at startup and **fails fast** if they don't match, but never issues DDL itself — this is what a migration-tool-managed schema (Flyway/Liquibase) pairs with. `update` — Hibernate compares entity metadata to the live schema and issues `CREATE`/`ALTER` statements to reconcile them automatically. `create`/`create-drop` — wipes and rebuilds the schema every startup (test/demo only). This project deliberately dropped Flyway and moved to `update`: it's a single-developer, non-production app, and a full versioned-migration workflow (migration files, checksums, repeatable-migration rules) was judged to be process overhead disproportionate to the project's actual needs.

<details>
<summary>Follow-ups</summary>

- What's the risk of `ddl-auto=update` in a real production system? (No migration history or rollback path; Hibernate's schema-diffing is a black box — it can miss destructive changes like a dropped/renamed column, silently leaving orphaned data or getting the mapping subtly wrong; multiple app instances racing to alter the schema concurrently on deploy is a real hazard.)
- If this project *did* need to reintroduce Flyway, what would that involve? (Add `spring-boot-starter-flyway` + `flyway-database-postgresql`, write versioned `V1__...sql`, `V2__...sql` migration files under `src/main/resources/db/migration`, and flip `ddl-auto` back to `validate` so Hibernate only checks, never mutates.)
- What's the actual difference between Flyway and Liquibase? (Flyway: plain versioned SQL files, simpler mental model, SQL-first. Liquibase: XML/YAML/JSON changesets, database-agnostic abstractions, supports rollback changesets natively — more powerful, more indirection.)
- Why does `spring-boot-starter-flyway` even need to exist as a separate starter in Boot 4, when `flyway-core` used to be enough in Boot 3? (Boot 4 modularized Flyway's autoconfiguration out of the monolithic `spring-boot-autoconfigure` jar into a dedicated `spring-boot-flyway` module — a real migration gotcha this project hit and diagnosed; see [§20](#20-project-deep-dive--behavioral).)
</details>

---

## 8. PostgreSQL & transactions

**Q: What does ACID mean, and where do you see each property show up (even implicitly) in this app?**
A: **Atomicity** — each individual `save()` call is all-or-nothing (though, as covered above, the *two* saves in `processPdf` are not atomic *together*, by design). **Consistency** — the auto-generated `CHECK` constraint on `status` (from `@Enumerated(STRING)`) enforces only valid enum values ever land in the column. **Isolation** — concurrent requests each get their own transaction/connection from the HikariCP pool; Postgres's default `READ COMMITTED` isolation level means one transaction won't see another's uncommitted writes. **Durability** — once a `save()` transaction commits, the row survives a crash — which is the entire reason the `PENDING` row is saved *before* calling the external OCR API.

<details>
<summary>Follow-ups</summary>

- What's the default Postgres isolation level, and what anomaly does it *not* prevent? (`READ COMMITTED` — prevents dirty reads, but allows non-repeatable reads and phantom reads. `REPEATABLE READ` and `SERIALIZABLE` are stronger, at a throughput cost.)
- What's HikariCP, and why does it matter here? (Spring Boot's default, auto-configured JDBC connection pool — reuses physical DB connections instead of opening one per request, which is far too slow/expensive. No explicit `HikariConfig` bean exists in this codebase — it's running on Boot's defaults, which is fine at this scale but wouldn't be tuned for real production load, e.g. `maximum-pool-size`.)
- Real incident from this project: a JVM default timezone alias (`Asia/Calcutta`, the legacy name for `Asia/Kolkata`) wasn't recognized by the Postgres JDBC driver's connection-startup handshake, and the app failed to connect. How was it fixed? (Pinned the JVM's `user.timezone` system property to `UTC` explicitly in the Gradle `bootRun`/`test` tasks, sidestepping the ambiguous alias entirely — a good example of an environment-dependent bug that's invisible until you run on a machine with a specific locale/timezone default.)
</details>

---

## 9. Exception handling

**Q: `GlobalExceptionHandler` is `@RestControllerAdvice` with a single `@ExceptionHandler(MaxUploadSizeExceededException.class)`. What's the mechanism, and why is this exception special (it isn't thrown by the controller code at all)?**
A: `@RestControllerAdvice` (= `@ControllerAdvice` + `@ResponseBody`) registers a class whose `@ExceptionHandler` methods intercept exceptions thrown by *any* `@RestController` in the application — centralizing error-response formatting instead of try/catch in every controller method. `MaxUploadSizeExceededException` is thrown by Spring's multipart-resolving machinery **before** the request body ever reaches `DocOcrController.uploadPdf` — it's a consequence of `spring.servlet.multipart.max-file-size=5MB` being enforced at the servlet/filter level. Without this handler, that exception would produce Spring Boot's default `500` HTML error page instead of a clean JSON `413`.

<details>
<summary>Follow-ups</summary>

- Why `HttpStatus.valueOf(413)` instead of `HttpStatus.PAYLOAD_TOO_LARGE`? (The named enum constant was deprecated in Spring Framework 7 — shipped as part of Boot 4.0.1's Spring 7 baseline — so the numeric `valueOf(413)` sidesteps the deprecation warning while producing the identical status.)
- What's `ProblemDetail` (RFC 7807), and how does it relate here? (A newer, Spring-native standardized error-response shape — `type`/`title`/`status`/`detail`/`instance` fields — that `@ExceptionHandler` methods can return directly instead of a hand-rolled DTO. This project uses its own `OcrResponse` shape for errors instead, which is a reasonable but non-standardized choice worth mentioning if asked "how would you make error responses more consistent industry-wide?")
- How would you unify the 400/415 manual-`ResponseEntity` error paths in the controller with the 413 path handled centrally here? (Throw typed exceptions from the controller/service and add corresponding `@ExceptionHandler` methods, so *all* error-response formatting lives in one place — see [§4](#4-spring-mvc--rest-layer) follow-ups.)
</details>

---

## 10. Externalized configuration

**Q: `OcrUploadProperties` uses `@ConfigurationProperties(prefix = "ocr.upload")` with a `List<String> allowedContentTypes` field, bound from `ocr.upload.allowed-content-types=application/pdf` in `application.properties`. Why use this over `@Value("${ocr.upload.allowed-content-types}")`?**
A: `@ConfigurationProperties` gives **type-safe, structured** binding — a whole related group of properties binds onto one POJO with IDE auto-completion support (given the annotation processor), validation support (`@Validated` + JSR-303 annotations), and relaxed binding (`allowed-content-types` in properties automatically maps to `allowedContentTypes` in Java — kebab-case/camelCase/snake_case are all interchangeable). `@Value` is fine for a single scalar injected in one place, but doesn't scale to lists/nested objects/validation the way `@ConfigurationProperties` does.

<details>
<summary>Follow-ups</summary>

- What does relaxed binding actually cover? (Kebab-case, camelCase, and env-var `UPPER_SNAKE_CASE` are all treated as equivalent — e.g., `ocr.upload.allowed-content-types`, `ocr.upload.allowedContentTypes`, and `OCR_UPLOAD_ALLOWEDCONTENTTYPES` can all bind to the same field, letting properties files and env vars use their respective natural conventions.)
- Where's the risk in storing `spring.security.user.password` as a plain property, even with an env-var override? (It's still just a string sitting in process environment/config — no encryption at rest, easy to leak via logs, process listing, or a misconfigured `/actuator/env`. A secrets manager, e.g. Vault or a cloud provider's secret store, is the real production answer.)
</details>

---

## 11. CORS

**Q: `CorsConfig implements WebMvcConfigurer` and calls `registry.addMapping("/api/**").allowedOriginPatterns("*").allowedMethods("GET","POST").allowedHeaders("*")`. What problem does this solve, and why is it dangerous as-is?**
A: CORS (Cross-Origin Resource Sharing) is a **browser-enforced** restriction — by default, JavaScript running on `https://frontend.example.com` cannot call `https://api.example.com` unless the API explicitly opts in via CORS response headers. This config opts every `/api/**` path into accepting requests from *any* origin (`*`), for GET/POST, with any headers — necessary for local frontend development against this backend, but far too permissive for a real deployment where you'd want to name the actual frontend origin explicitly.

<details>
<summary>Follow-ups</summary>

- What's a CORS "preflight" request? (For "non-simple" requests — custom headers like `Authorization`, non-GET/POST/HEAD methods, or non-form content types — the browser first sends an `OPTIONS` request asking the server which origins/methods/headers are allowed, *before* sending the real request. Spring's `CorsFilter`/MVC CORS handling answers this automatically once configured.)
- Why don't wildcard origins (`*`) work if you also need to send credentials (cookies, `Authorization` headers) cross-origin? (The CORS spec forbids combining `Access-Control-Allow-Origin: *` with `Access-Control-Allow-Credentials: true` — browsers reject it outright. You must echo back a specific, validated origin instead of `*` when credentials are involved. This project uses HTTP Basic, whose `Authorization` header isn't a "credentialed" cookie in the CORS sense, but the same principle applies once cookies/sessions enter the picture.)
- What are the alternatives to `WebMvcConfigurer#addCorsMappings`? (A standalone `CorsFilter` bean for finer control over filter ordering; the `@CrossOrigin` annotation for per-controller/per-method rules instead of global config.)
</details>

---

## 12. Design patterns actually used here

**Q: Name the design patterns you can point to concretely in this codebase — not textbook definitions, actual usages.**

| Pattern | Where | Why |
|---|---|---|
| **Dependency Injection / IoC** | Constructor injection throughout (`DocOcrController`, `OcrServiceImpl`) | Decouples object creation from object use; Spring's `ApplicationContext` is the IoC container |
| **Repository** | `OcrJobRepository extends JpaRepository` | Abstracts persistence behind a collection-like interface; implementation is a dynamic proxy generated at runtime |
| **DTO (Data Transfer Object)** | `OcrResponse`, `OcrSpaceResponse` | Decouples the API's public response shape and the third-party API's JSON shape from the internal `OcrJob` entity — entities never leak directly over the wire |
| **Adapter** | `OcrSpaceResponse` + `parsePdf()` | Translates OCR.space's PascalCase, third-party JSON contract into this application's own domain shape |
| **Facade** | `OcrServiceImpl.processPdf` | Presents one simple method (`processPdf`) that internally orchestrates persistence, an HTTP call, and response parsing — callers don't see that complexity |
| **Singleton** (via Spring bean scope) | Every `@Service`/`@Configuration`-declared `@Bean` (`RestTemplate`, `LoggerService`, etc.) | Default Spring bean scope is one shared instance per `ApplicationContext` |
| **Proxy** | Spring Data JPA repository implementations; Hibernate lazy-loading entity proxies; Spring AOP (e.g., `@Transactional` interception) | Spring/Hibernate generate runtime proxies that intercept method calls to add behavior (transactions, persistence, lazy fetch) transparently |
| **Chain of Responsibility** | The Servlet filter chain / Spring Security's `SecurityFilterChain` | Each filter (auth, CORS, exception translation) gets a chance to handle or pass along the request before it reaches `DispatcherServlet` |
| **Front Controller** | `DispatcherServlet` (Spring MVC's core, configured implicitly by `spring-boot-starter-web`) | Single entry point that routes every incoming HTTP request to the right `@RestController` method |

<details>
<summary>Follow-ups</summary>

- Where would a **Strategy** pattern fit if this app needed to support multiple OCR providers (OCR.space, AWS Textract, Google Vision)? (Extract an `OcrProvider` interface with `extractText(MultipartFile)`, implement one per vendor, and inject the active one via config — `OcrServiceImpl` becomes provider-agnostic.)
- Where would a **Builder** help? (`OcrResponse`'s telescoping constructors — see [§1](#1-core-java).)
- Is `@RestControllerAdvice`'s exception handling itself a design pattern? (Arguably Chain of Responsibility again, or more specifically the Interceptor pattern — it centralizes cross-cutting error-handling logic instead of scattering try/catch everywhere, similar in spirit to AOP.)
</details>

---

## 13. HTTP clients: RestTemplate, WebClient, RestClient

**Q: `OcrServiceImpl` calls OCR.space via `RestTemplate.exchange(...)`. Is `RestTemplate` still the right choice in 2026?**
A: No — `RestTemplate` has been in maintenance mode (feature-frozen, not deprecated-for-removal but explicitly "no longer under active development") since Spring 5, in favor of `WebClient` (reactive, non-blocking, part of Spring WebFlux) and, more recently, `RestClient` (introduced in Spring 6.1 — a **synchronous**, fluent-API client that keeps `RestTemplate`'s blocking simplicity but with a modern, chainable API). This app is a low-traffic, synchronous-by-design service, so blocking I/O isn't inherently wrong here — but `RestTemplate` specifically is the legacy choice; `RestClient` would be the direct drop-in modernization with no architecture change required.

<details>
<summary>Follow-ups</summary>

- This app configures **no timeout** on the `RestTemplate` call to OCR.space — what's the actual risk? (An unresponsive OCR.space API can hang the calling thread indefinitely, tying up a servlet container thread per stuck request — under load this exhausts the thread pool and takes the whole app down. Always set explicit connect + read timeouts on any outbound HTTP client.)
- How would you add a timeout to this `RestTemplate`? (Configure the underlying `ClientHttpRequestFactory` — e.g., `SimpleClientHttpRequestFactory` or a `HttpComponentsClientHttpRequestFactory` — with `setConnectTimeout`/`setReadTimeout`, and pass it into the `RestTemplate` bean in `AppConfig`.)
- What's the difference between `WebClient` and `RestClient`? (Both are fluent/chainable; `WebClient` is reactive — returns `Mono`/`Flux`, needs a reactive runtime (Netty by default) and non-blocking downstream code to actually benefit; `RestClient` is synchronous/blocking like `RestTemplate` but with `WebClient`'s ergonomic API — no reactive-programming overhead needed.)
- How would you add resilience (retry, circuit breaker, timeout-as-a-policy) around this external call? (Resilience4j — `@Retry`, `@CircuitBreaker` annotations or programmatic decorators — wrapping the OCR.space call so transient failures retry with backoff and sustained failures fail fast instead of cascading.)
</details>

---

## 14. Jackson & JSON mapping

**Q: `OcrSpaceResponse` uses `@JsonProperty("OCRExitCode")` on a field named `ocrExitCode`, and the class is annotated `@JsonIgnoreProperties(ignoreUnknown = true)`. Why both?**
A: OCR.space's actual JSON response uses PascalCase keys (`"OCRExitCode"`, `"ParsedText"`) which don't match Java field-naming conventions — `@JsonProperty` maps the exact wire-format key to an idiomatically-named Java field. `@JsonIgnoreProperties(ignoreUnknown = true)` makes deserialization tolerant of extra fields the third-party API might add or already sends that this DTO doesn't model — without it, Jackson throws `UnrecognizedPropertyException` on any field not explicitly declared, which is far too brittle for consuming an external API you don't control the shape of.

<details>
<summary>Follow-ups</summary>

- `OcrServiceImpl.parsePdf` does `objectMapper.convertValue(response, OcrSpaceResponse.class)` where `response` is already a `Map<String,Object>` (from `restTemplate.exchange(..., Map.class)`), rather than deserializing the HTTP response directly into `OcrSpaceResponse`. Why is that a code smell? (Double conversion — JSON → `Map` → DTO — is strictly less efficient and less type-safe than JSON → DTO directly. It also means malformed/unexpected JSON shape errors surface later and more confusingly, at the `convertValue` step, instead of immediately during HTTP response deserialization. The fix: call `restTemplate.exchange(url, POST, entity, OcrSpaceResponse.class)` directly.)
- A **new `ObjectMapper()` is instantiated on every single call** to `parsePdf` instead of being injected as a shared bean. Why does that matter? (`ObjectMapper` is thread-safe and explicitly designed to be a shared singleton after configuration — creating a fresh one per call is wasted allocation/init cost for no benefit, and if any custom configuration (a module, a naming strategy) is ever added, it'd need to be duplicated everywhere the class is instantiated instead of configured once.)
- What's the difference between `ObjectMapper.readValue()` and `.convertValue()`? (`readValue` parses raw JSON text/bytes/stream into a Java type; `convertValue` converts between two already-in-memory Java representations, e.g. `Map` → POJO, by internally serializing then deserializing — useful for exactly this kind of situation, but a sign that an earlier `readValue` call already lost type information.)
</details>

---

## 15. Logging

**Q: `LoggerService` wraps a single `LoggerFactory.getLogger("app-logger")` and exposes `info/warn/debug/error` pass-through methods, used everywhere instead of each class getting its own `private static final Logger log = LoggerFactory.getLogger(ThisClass.class)`. What's the trade-off?**
A: SLF4J is a **facade** — an abstraction over a concrete logging implementation (Logback, by Spring Boot's default). The idiomatic pattern is one logger *per class*, named after that class, so log output naturally shows its origin and each class's log level can be tuned independently. This project instead centralizes into one shared logger named `"app-logger"` — every log line looks like it came from the same source, and you lose the ability to set, say, `OcrServiceImpl` to `DEBUG` while leaving everything else at `INFO`.

<details>
<summary>Follow-ups</summary>

- What's MDC (Mapped Diagnostic Context), and how would you use it here to trace a single upload request across log lines? (`MDC.put("jobId", job.getId().toString())` at the start of request handling — Logback pattern layouts can then include `%X{jobId}` in every subsequent log line for that thread, letting you `grep` one request's full trail out of interleaved concurrent logs.)
- What's "structured logging," and why is it trending? (Emitting logs as JSON key-value objects instead of freeform text strings, so log aggregators (ELK, Datadog, Loki) can query/filter/aggregate on fields directly instead of regex-parsing text. Spring Boot 3.4+ added native structured logging support.)
- Parameterized logging (`logger.info("processed {} pages", count)`) vs string concatenation (`logger.info("processed " + count + " pages")`) — why does it matter? (String concatenation always builds the string, even if that log level is disabled; `{}` placeholders defer formatting until SLF4J confirms the level is enabled — a real (if small) performance difference at high log volume.)
</details>

---

## 16. Testing (and this repo's gap)

**Q: The project currently has one test file — an application-context-load smoke test. How would you actually test this backend properly?**
A: Layer it: **unit tests** for `OcrServiceImpl.parsePdf` with Mockito-mocked `RestTemplate`/`OcrJobRepository` (fast, no Spring context, tests pure logic like the `\n---\n` page-joining and error-message extraction). **Slice tests** for the controller with `@WebMvcTest(DocOcrController.class)` + `MockMvc`, mocking `OcrService` via `@MockBean` — verifies routing, status codes, and content-type validation without booting the full app or touching a real database. **Integration tests** with `@SpringBootTest` + **Testcontainers** spinning up a real ephemeral Postgres container — verifies the actual JPA mapping, the auto-generated schema (`ddl-auto=update`), and the full save-call-save flow against a real database engine, not an in-memory substitute.

<details>
<summary>Follow-ups</summary>

- Why Testcontainers over an in-memory database like H2 for the integration tests? (H2's SQL dialect and type system differ subtly from Postgres — a test passing against H2 can still fail against real Postgres, e.g. around the `TEXT` column type or the enum `CHECK` constraint discussed in [§6](#6-spring-data-jpa--hibernate). Testcontainers gives you the *actual* database engine in a disposable Docker container, matching prod.)
- How would you test the `MaxUploadSizeExceededException` → 413 path specifically? (`MockMvc` with a `MockMultipartFile` larger than 5MB posted to the endpoint, asserting the response status and body — this exercises `GlobalExceptionHandler` end-to-end.)
- How would you test `SecurityConfig` — that unauthenticated requests get 401 and `/actuator/health` doesn't? (`@WebMvcTest` (or full `@SpringBootTest`) with `MockMvc`, asserting `.andExpect(status().isUnauthorized())` for the protected endpoint without credentials, and `.andExpect(status().isOk())` for `/actuator/health`.)
- What's the risk of testing only "the happy path"? (Misses exactly the kind of bug already living in this codebase — e.g., the `LoggerService.error()` format-string bug in [§1](#1-core-java) — a unit test asserting the *actual formatted log message* would have caught it immediately.)
</details>

---

## 17. File upload handling & security

**Q: The controller checks `file.getContentType()` against `OcrUploadProperties.getAllowedContentTypes()` to enforce "PDF only." What's the security gap in that approach?**
A: `MultipartFile.getContentType()` returns whatever `Content-Type` the **client claims** in the multipart request — it's a header value, entirely attacker-controlled, not derived from inspecting the actual file bytes. Anyone can rename `payload.exe` to `payload.pdf` and set `Content-Type: application/pdf` in the request, sailing straight past this check. This is a real, common vulnerability class (unrestricted file upload via content-type spoofing).

<details>
<summary>Follow-ups</summary>

- How would you actually validate the file is a PDF? (Magic-byte/MIME sniffing on the actual file content — e.g. Apache Tika (`Tika.detect(InputStream)`) or checking the PDF file signature (`%PDF-` at the start of the byte stream) — rather than trusting the header.)
- Beyond content-type spoofing, what else would you check before forwarding an uploaded file to an external API? (Filename sanitization — reject path traversal characters (`../`) if the filename is ever used to construct a file path; a virus/malware scan if files are stored, not just proxied; the 5MB size cap already in place (`spring.servlet.multipart.max-file-size`) mitigates resource-exhaustion/DoS via oversized uploads.)
- The file is streamed straight into a multipart request to OCR.space (`body.add("file", file.getResource())`) without ever being persisted to disk. What's the security trade-off of streaming vs. saving-then-processing? (Streaming avoids leaving attacker-controlled file content sitting on the server's filesystem at all — smaller attack surface, no cleanup-of-temp-files concern — at the cost of not being able to re-process a file without the client re-uploading it, since nothing is retained beyond the extracted text in `OcrJob`.)
</details>

---

## 18. System design extensions

These are the natural "now scale it" follow-ups a 3-YOE candidate should expect once the interviewer has walked through the actual code.

**Q: OCR.space calls are synchronous and block the request thread for the entire external-API round-trip. How would you make this asynchronous?**
A: Return `202 Accepted` immediately with the `PENDING` job's `id` after the initial `save()`, and hand the actual OCR call off to a message queue (RabbitMQ/SQS) or a `@Async`-annotated method backed by a dedicated thread pool. A separate consumer/worker processes the job and updates its status; the client polls `GET /api/ocr/v0/pdf/{id}` (a new endpoint) or is notified via webhook/WebSocket when done.

<details>
<summary>Follow-ups</summary>

- What does the `OcrJob.status` state machine (`PENDING → SUCCESS`/`FAILED`) already give you toward this design? (It's already exactly the polling model needs — the hard part (a persisted, queryable job record) is done; "make it async" is really just "stop blocking the HTTP thread while waiting," not a data-model change.)
- How would virtual threads (Project Loom, stable since Java 21) change this calculus? (A blocking call on a virtual thread doesn't pin a scarce platform/OS thread the way it does today — for I/O-bound workloads like this exact external-API call, virtual threads can make "just block, but on a virtual thread" a legitimate alternative to full async/reactive rearchitecture. Spring Boot 3.2+ has first-class virtual-thread support via `spring.threads.virtual.enabled=true`.)
</details>

**Q: How would you rate-limit this endpoint, and why might you want to?**
A: The demo OCR.space API key (`helloworld`) is itself rate-limited, and even a paid key has quotas — an unthrottled endpoint lets one caller exhaust the whole app's quota. Options: a `Bucket4j`-based in-app rate limiter keyed by authenticated user, or push it up a layer to an API gateway (e.g., Spring Cloud Gateway, or a reverse proxy like nginx) in front of the service.

**Q: How would you support multiple users/tenants instead of the single hardcoded Basic-auth user?**
A: A `User` entity + Spring Security `UserDetailsService` backed by the database, hashed passwords via `BCryptPasswordEncoder`, and a foreign key from `OcrJob` to the owning user so job history/queries can be scoped per-caller — directly extending the persistence model already in place.

---

## 19. Recent trends (2025–2026 interview radar)

**Q: This app is on Java 22 and Spring Boot 4.0.1 — what should you know about *why* that's notable right now?**
A: Spring Boot 4 (late 2025) is the first major line built on the Spring Framework 7 / Jakarta EE 11 baseline — continuing the `javax.*` → `jakarta.*` package migration that started in Boot 3, plus the module-splitting pattern this project hit firsthand with Flyway ([§7](#7-schema-management-hibernate-ddl-auto-vs-flyway)). Expect interviewers to probe whether you understand *why* frameworks modularize this way (smaller, more purpose-built starters instead of one do-everything jar) rather than just knowing the trivia.

<details>
<summary>Follow-ups</summary>

- **Virtual threads** (Java 21+, Project Loom) — what problem do they solve, and where wouldn't they help? (Solve thread-per-request scalability for I/O-bound blocking code without rewriting to reactive; don't help CPU-bound work, and can expose latent thread-safety bugs in code that assumed cheap thread-local isolation was "free" at low thread counts.)
- **Records** (Java 16+) — this project's DTOs (`OcrResponse`, `OcrSpaceResponse`) are hand-written mutable classes with getters/setters. Why might a modern rewrite use `record` instead? (Immutability, less boilerplate, built-in `equals`/`hashCode`/`toString` — though Jackson deserialization support for records needs either the `jackson-databind` version that supports the canonical constructor directly, or `@JsonCreator`, which is transparent in current Jackson but worth knowing.)
- **GraalVM native image** — what's the pitch, and what would break in this app? (Ahead-of-time compilation to a native binary — near-instant startup, lower memory footprint, ideal for serverless/container cold-starts. Reflection-heavy code (a lot of what Hibernate, Jackson, and Spring itself do) needs explicit "reachability metadata" hints to work under native image — Spring Boot 3+ has built-in AOT processing support for this.)
- **Observability** — Micrometer + OpenTelemetry as the modern standard for metrics/tracing, vs. this app's current bare SLF4J logging with no request tracing/correlation IDs, no metrics endpoint beyond default Actuator health.
- **API versioning in the URL** (`/api/ocr/v0/pdf`) — this project already does this; be ready to discuss why URL versioning was chosen over header-based versioning (see [§4](#4-spring-mvc--rest-layer)) as a "tell me about a design decision" prompt.
- **Containerization** — this project ships a `docker-compose.yml` for local Postgres but the Spring Boot app itself isn't containerized yet. Expect "how would you Dockerize this?" (multi-stage `Dockerfile` — Gradle build stage, then a slim JRE runtime stage copying just the built jar) and "how would you deploy it?" (container to ECS/Cloud Run/Kubernetes, secrets via the platform's secret manager instead of env vars in a compose file).
</details>

---

## 20. Project deep-dive / behavioral

These map directly to "walk me through a decision you made" and "tell me about a bug you found" prompts — answer them as *stories*, not definitions.

**Q: "Tell me about a tricky bug or misconception you had to correct while building this."**
A: *The Flyway story.* Adding PostgreSQL persistence, the plan called for Flyway migrations. After adding `flyway-core` + `flyway-database-postgresql` to `build.gradle`, no migration ever ran — the table simply didn't exist at startup, throwing a schema-validation error. The apparent evidence (`FlywayAutoConfiguration` seemingly absent from the shipped auto-configure jar) led to writing a manual workaround: a hand-rolled `Flyway` bean with `initMethod = "migrate"`, wired to run before JPA initialized via a `BeanFactoryPostProcessor` forcing a `dependsOn` ordering. It worked, but was a strong signal something was off — that's not how a mature framework should require you to wire a first-party integration. The actual root cause, caught on review: Spring Boot 4 moved Flyway's autoconfiguration out of the monolithic `spring-boot-autoconfigure` jar into a dedicated `spring-boot-flyway` module, only pulled in transitively via the `spring-boot-starter-flyway` starter — `flyway-core` alone, sufficient in Boot 3.x, silently configures nothing in 4.x. Fix: swap to the starter, delete the manual workaround entirely, re-verify against a fresh container. **The lesson volunteered unprompted in an interview:** a workaround that *works* isn't the same as a workaround that's *correct* — when a well-established, actively-maintained integration seems to need a hand-rolled bypass, that's usually a signal to dig into *why*, not a green light to ship the bypass.

**Q: "Then why did you rip Flyway back out again days later?"**
A: Different axis of the same judgment call: once Flyway was working *correctly*, the next question was whether it was *warranted* for this specific project — a non-production, single-developer app. Flyway's value is real but conditional: versioned migration history, safe rollback, and multi-environment schema consistency all matter when multiple people or environments touch the same evolving schema. None of that was true here yet. `ddl-auto=update` gets equivalent day-to-day functionality (a live schema matching the entity) with far less process overhead — with the explicit trade-off, documented in this repo's README under "Future scope," that it comes back the moment the project needs real migration history or moves toward shared/prod use.

**Q: "Walk me through the request flow for a PDF upload, end to end."**
A: `POST /api/ocr/v0/pdf` with HTTP Basic credentials and a multipart `file` field → Spring Security's filter chain authenticates the request → Spring's multipart resolver enforces the 5MB size cap (throwing `MaxUploadSizeExceededException`, caught centrally, if exceeded) → `DocOcrController` checks the file isn't empty and its claimed content-type is in the allowed list → `OcrServiceImpl.processPdf` persists an `OcrJob` row with status `PENDING` → builds and sends a multipart request to the OCR.space API via `RestTemplate` → on response, converts the `Map` body into an `OcrSpaceResponse` DTO, walks `ParsedResults[]`, joins each page's text with `"\n---\n"` → updates the same `OcrJob` row to `SUCCESS` with the extracted text (or `FAILED` with the error message, on any exception) → controller returns `200` with `{ id, filename, text, status }`.

**Q: "What would you improve first if you kept working on this?"**
A: In priority order, grounded in real risk rather than polish: (1) timeouts on the outbound `RestTemplate` call — an unresponsive OCR.space API can currently hang a request thread indefinitely; (2) magic-byte file-type validation instead of trusting the client-supplied `Content-Type` header; (3) hashed credentials via a real `PasswordEncoder` instead of a plaintext-property single user; (4) actual test coverage beyond the one context-load smoke test; (5) a scheduled sweep for `OcrJob` rows stuck in `PENDING` from a crash mid-flight.
