# sotres — ISO8583 ↔ REST gateway

## Stack
- Java 25, Spring Boot 3.5.16
- Servlet MVC (Tomcat) + virtual threads — **not** reactive; Reactor is not on the classpath
- jReactive-8583 (Netty) ISO8583 transport; every inbound message is handed off the event loop
  onto `isoTransactionAsyncTaskExecutor` (virtual threads) before any work
- Spring Data JPA / Hibernate on PostgreSQL; schema is external (`src/main/resources/ddl.sql`,
  `spring.jpa.hibernate.ddl-auto=none`)
- Log4j2 + LMAX Disruptor async logging; Micrometer tracing (Brave/B3 + W3C)

## Architecture notes
- Blocking-on-virtual-threads throughout: JSLT, JDBC, and the outbound `RestClient` are plain
  synchronous calls. Never block the Netty event loop — hand off first.
- Response correlation: one `CorrelationRegistry` (two-window Caffeine — in-flight
  `CompletableFuture` map + `registered` grace window) backs both `CALLBACK` (fire-and-forget,
  reply handled by `TransactionResponseParticipant`) and `RESPONSE` (`EnhancedIsoClient.send`
  blocks the virtual thread). Mode is `CLIENT_REGISTRY_TYPE` + per-selector `system_properties`.
- `HeaderFilter` (OncePerRequestFilter) builds `ContextDTO` + starts the `Observation`;
  `EventLogInterceptor` (afterCompletion) writes the `event_logs` audit row.
- Dead-letter retry (`dead_letter_process` + `AbstractRetryProcessorService`) is a wired-but-empty
  extension point — no producer, no concrete processor ship with the boilerplate.

## Commands
- build + test + coverage: `JAVA_HOME=~/.sdkman/candidates/java/25-tem mvn -o verify`
  (full run ~2–3 min; E2E uses Testcontainers Postgres — Docker must be running)
- fast compile check: `mvn -o -q test-compile`
- run locally: `mvn spring-boot:run` (defaults target `localhost:5432/boilerplate`, `postgres`/`changeme`)
- coverage floor (JaCoCo, enforced in `verify`): INSTRUCTION 73.8 / BRANCH 70.2 / LINE 75.9

## Conventions
- Structured logs: `log.info(AppLogMessage.message("...", args).isoMessage(msg).error(t))`
- New env var → add to `docs/ENVIRONMENT_VARIABLES.md`; new endpoint → `docs/api-reference.md`
- `docs/REFACTOR_PLAN.md` is git-ignored — never `git add` it
