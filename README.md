# URL Shortener

A REST API that turns a long URL into a short code and redirects visitors back to the
original. Two endpoints, no accounts, no analytics.

The domain is trivial on purpose. This is a warm-up project written while learning Spring
Boot, so the framework is the only unfamiliar part. Java 21, Spring Boot 4.1.1,
PostgreSQL 17, Flyway, Gradle.

## API

**`POST /api/urls`** with `{"url": "https://www.warwick.ac.uk"}` returns `201` with a
`Location` header and `{"code": "8Ywuiice"}`. Returns `400` if `url` is missing, empty or
whitespace.

**`GET /{code}`** returns `302` with the original URL in the `Location` header, or `404`
if the code does not exist.

## Running it

Requires Java 21 and Docker. Gradle is not needed, the wrapper handles it.

```bash
docker run --name url-shortener-db \
  -e POSTGRES_DB=urlshortener \
  -e POSTGRES_USER=urlshortener \
  -e POSTGRES_PASSWORD=localdev \
  -p 5432:5432 -d postgres:17
```

On later runs the container already exists, so start it instead:

```bash
docker start url-shortener-db
```

Run the app. Flyway applies pending migrations on startup:

```bash
./gradlew bootRun
```

Build and run tests:

```bash
./gradlew build
```

Try it:

```bash
curl -i -X POST http://localhost:8080/api/urls \
  -H "Content-Type: application/json" \
  -d '{"url":"https://www.warwick.ac.uk"}'
```

```bash
curl -i http://localhost:8080/YOURCODE
```

On Windows use Git Bash. In PowerShell `curl` is an alias for `Invoke-WebRequest` and
takes different arguments, so use `curl.exe` there.

Inspect the database:

```bash
docker exec url-shortener-db psql -U urlshortener -d urlshortener -c "SELECT * FROM urls;"
```

Reset to a clean schema by destroying the container. Flyway replays every migration from
scratch:

```bash
docker rm -f url-shortener-db
```

## Design decisions

**Flyway owns the schema, Hibernate only validates.** `ddl-auto` is `validate`, so
Hibernate compares the entity mapping to the tables at startup and refuses to boot if
they disagree. `update` was rejected because it is non-deterministic, never drops
anything (a removed field leaves its column forever, a rename orphans the data), cannot
migrate data, and leaves no reviewable record or rollback path. Spring Boot makes
`entityManagerFactory` depend on `flyway`, so migrations run first.

**Surrogate primary key.** `id BIGINT GENERATED ALWAYS AS IDENTITY`, with `short_code` a
separate `NOT NULL UNIQUE` column. The code alone would have worked as a key, but keys
are what other tables reference and are painful to change, so keeping the key free of
business meaning means codes could be regenerated or reformatted without touching
anything pointing at the row. The `id` never appears in the API. In Postgres the `UNIQUE`
constraint also creates the btree index that makes redirect lookups fast, so no separate
index is needed.

**302 rather than 301.** Browsers cache a 301 indefinitely, so the server never sees
repeat visitors. That would make click tracking impossible to add later and prevents
repointing a code. During development it also means a cached redirect survives data
changes, which is confusing to debug. 302 costs a round trip and keeps the options open.

**`SecureRandom` for code generation.** Eight characters from a 62 character alphabet.
`java.util.Random` is a linear congruential generator, so observing a few outputs lets
you predict the rest, which would let anyone enumerate other people's links.
`SecureRandom` is API compatible.

**Collisions handled by the unique constraint, not a pre-check.** 62^8 is about
2.2 x 10^14 codes, so the chance of a collision is roughly n / 2.2 x 10^14, around one in
two million even at a hundred million rows. Checking first would not have made this
correct anyway, since two concurrent requests can both see a code as free. The constraint
is the only real guarantee. The accepted trade-off is that a collision surfaces as a 500.

**DTOs at the HTTP boundary.** The controller takes `CreateUrlRequest` and returns
`CreateUrlResponse` rather than the entity, so the API contract stays independent of the
schema and clients cannot set fields the database owns. The DTOs are records and the
entity is not: a DTO is an immutable value, while JPA needs a no-arg constructor and
mutable fields.

**Constructor injection.** Dependencies are `final` fields assigned in the constructor,
no `@Autowired`. Fields can be final and non-null, dependencies are visible in the
signature, and classes can be instantiated directly in tests with no Spring context.

**Layer separation.** The controller handles HTTP and knows nothing about code
generation. The service holds the logic and knows nothing about HTTP. At this size the
service is close to a pass-through, which is a fair criticism. It earns its place by
owning code generation and the transaction boundaries, and by being callable without a
web request.

**Column types.** `short_code` is `VARCHAR(8)` because the length is fixed and known.
`original_url` is `TEXT` because there is no useful maximum. In Postgres the two are
stored and perform identically, so `n` is a constraint rather than an optimisation.
`created_at` is `TIMESTAMPTZ`, since `TIMESTAMP` has no zone and becomes ambiguous once
the app runs anywhere but the machine that wrote the row.

**Boot 4, not the 3.x originally planned.** Initializr no longer offers any 3.x version.
Boot 4 renamed several starters, so most tutorials will not match this build file:
`spring-boot-starter-webmvc` replaces `spring-boot-starter-web`, Flyway has a real
starter instead of adding `flyway-core` by hand, and `spring-boot-starter-test` is split
into per-feature test starters.

**Postgres driver is `runtimeOnly`.** It is on the runtime classpath but not the compile
classpath, so application code cannot import `org.postgresql.*` and the database stays a
configuration concern.

## Known limitations

Deliberate. The project was scoped to two endpoints and stops there.

- `@NotBlank` does not validate URL format, so `{"url":"hello"}` is accepted and stored.
- The response returns a bare code, not a full short URL, so the service does not need to
  know its own hostname.
- A code collision returns 500.
- No authentication, analytics, custom aliases or expiry.
- Container data is not on a volume, so removing the container discards it.
- The local database password is committed. It is a throwaway credential for a container
  listening only on localhost.

## Structure

```
src/main/java/io/github/gagann06/urlshortener/
├── UrlShortenerApplication.java   entry point, component scan root
├── UrlController.java             HTTP layer
├── UrlService.java                logic, transaction boundaries
├── UrlRepository.java             Spring Data JPA interface
├── Url.java                       JPA entity
├── CreateUrlRequest.java          request DTO
└── CreateUrlResponse.java         response DTO

src/main/resources/
├── application.yml
└── db/migration/V1__create_urls_table.sql
```

`@SpringBootApplication` component-scans from its own package downward, so classes must
sit at or below `io.github.gagann06.urlshortener` to be found.

Migrations are named `V<version>__<description>.sql` with two underscores. Flyway
checksums applied migrations, so an applied file must never be edited. Schema changes go
in a new versioned file.
