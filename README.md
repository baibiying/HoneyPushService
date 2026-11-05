# HoneyPush Service

Spring Boot backend for [HoneyPush](https://github.com/) — auth, tasks, focus sessions, stats, performance, AI parse/schedule, and officer preview video proxy.

## Prerequisites

- Java 21
- PostgreSQL 14+

## Environment

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export DATABASE_URL=jdbc:postgresql://localhost:5432/honeypush
export DATABASE_USER=postgres
export DATABASE_PASSWORD=postgres
```

Optional LLM (OpenAI-compatible):

```bash
export LLM_API_KEY=sk-...
export LLM_BASE_URL=https://api.openai.com/v1
export LLM_MODEL=gpt-4o-mini
```

Production cookie flag:

```bash
export COOKIE_SECURE=true
```

## PostgreSQL (Docker)

```bash
docker run --name honeypush-pg -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=honeypush -p 5432:5432 -d postgres:16
```

## Run

```bash
./gradlew bootRun
```

API base: `http://localhost:8080` — CORS allows `http://localhost:5173` with credentials.

Schema is managed outside the app. Hibernate only validates that entities match the existing tables (`spring.jpa.hibernate.ddl-auto=validate`).
