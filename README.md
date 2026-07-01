# URL Shortener Service

A full-stack URL shortening service built with Spring Boot, Angular, and PostgreSQL.

## Tech Stack

- **Backend** — Spring Boot 3, Java 21, Spring Data JPA, Caffeine Cache, Bucket4j
- **Frontend** — Angular 19
- **Database** — PostgreSQL 16
- **Infrastructure** — Docker Compose

## Features

- Shorten URLs with optional TTL (expiry)
- Redirect via short code
- Hit count tracking and stats per URL
- Per-IP rate limiting
- In-memory caching for fast redirects
- Scheduled cleanup of expired URLs

## Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/)
- Java 21
- Node.js

## Running Locally

> Start services in this order: **Docker → Backend → Frontend**

### 1. Database (PostgreSQL)

Copy the example env file and start the container:

```bash
cp .env.example .env
```

```powershell
docker compose up -d
```

To stop:
```powershell
docker compose down        # stop, keep data
docker compose down -v     # stop, wipe data
```

### 2. Backend (Spring Boot)

Run from the project root:

```powershell
.\mvnw.cmd spring-boot:run
```

Runs on **http://localhost:8090**

### 3. Frontend (Angular)

```powershell
cd frontend
npx ng serve
```

Runs on **http://localhost:4200**

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/shorten` | Create a short URL |
| `GET` | `/{shortCode}` | Redirect to original URL |
| `GET` | `/api/v1/stats/{shortCode}` | Get stats for a short URL |
| `DELETE` | `/api/v1/shorten/{shortCode}` | Deactivate a short URL |
| `GET` | `/actuator/health` | Health check |

### Shorten a URL

```bash
curl -X POST http://localhost:8090/api/v1/shorten \
  -H "Content-Type: application/json" \
  -d '{"url": "https://example.com", "ttlDays": 7}'
```
