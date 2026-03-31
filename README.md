# QPrint — Phase 1 (Student Client)

QPrint is a student-focused print shop automation platform. This repository contains the Phase 1 student client stack: Spring Boot microservices, Kafka/Redis/Postgres/MinIO infrastructure, and a Vite/React frontend.

## Architecture (high level)
```
[Frontend] -> [Gateway] -> /auth | /api/* routes
                        \-> Redis rate limiting

Kafka <-> Auth <-> Cart <-> Orders <-> Transactions <-> Checkout
Redis: cart, checkout, orders, otp
Postgres: auth/users, objects, transactions
MinIO: file storage
```

## Prerequisites
- Java 21
- Maven 3.9+
- Docker + Docker Compose
- Node 18+

## Quick start (Docker)
1. Copy `deploy/.env.dev.example` to `.env.dev` and fill secrets.
2. Ensure `.env.dev` uses a JWT secret that is unique to dev.
3. Validate secret separation before startup:
    - `./scripts/validate-jwt-secrets.ps1 -DevEnvPath .env.dev -StagingEnvPath deploy/.env.staging -ProdEnvPath deploy/.env.prod`
4. From repo root:
    - `docker compose --env-file .env.dev up --build`
5. Gateway is exposed on 8080; backend microservices are internal-only on the Docker network.
6. Infra containers (Postgres/Redis/Kafka/MinIO) are also internal-only by default in local compose.

## Frontend dev
```
cd QPrint-frontend
npm install
npm run dev
```

## Module ports (default)
- Gateway: 8080
- Auth: 8081 (internal container port)
- Objects: 8082 (internal container port)
- Cart: 8083 (internal container port)
- Checkout: 8084 (internal container port)
- Orders: 8085 (internal container port)
- OTP: 8086 (internal container port)
- Transactions: 8087 (internal container port)
- Shops: 8088 (internal container port)

## Backend security

### Security model
- Access tokens are JWTs signed by auth service and validated by gateway plus every backend microservice.
- JWT validation checks signature, expiration, issuer, and audience in each service.
- Role and scope claims are converted to Spring Security authorities (`ROLE_*`, `SCOPE_*`).
- Service-to-service HTTP calls relay user JWT when available.
- If a backend automation flow has no user token (for example webhook/event handling), services mint short-lived service JWTs with `role=SERVICE` and scoped permissions.
- Backend containers are not host-exposed in Docker Compose, so traffic must pass through gateway unless you intentionally re-open ports.

### Backend security column
| Service | Main routes | Security column (how it works) |
| --- | --- | --- |
| Gateway | `/auth/**`, `/api/**` | Validates JWT at edge with signature + `exp` + `iss` + `aud`; routes protected APIs through `JwtAuth` filter. |
| Auth | `/auth/**` | Issues JWT with role + scopes; validates JWT with issuer/audience on protected auth endpoints. |
| Objects | `/api/objects/**` | Re-validates JWT per request; enforces `objects:read`/`objects:write` scopes via method authorization. |
| Cart | `/api/cart/**` | Re-validates JWT per request; enforces `cart:read`/`cart:write` scopes via method authorization. |
| Checkout | `/api/checkout/**` | Re-validates JWT on user routes; enforces `checkout:read`/`checkout:write`; webhook path remains public by design. |
| Orders | `/api/orders/**`, `/internal/orders/**` | Re-validates JWT per request; `orders:read`/`orders:write` for user flows; `orders:manage` or service role for privileged status updates. |
| OTP | `/internal/otp/**` | Requires service-level JWT authority (`otp:write` or `ROLE_SERVICE`) for generate/verify endpoints. |
| Transactions | `/api/transactions/**` | Re-validates JWT per request; enforces `transactions:read` scope. |
| Shops | `/api/shops/**` | Re-validates JWT per request; enforces `shops:read` scope. |

### JWT claims used by backend
- `sub`: user UUID for user tokens, `service:<service-name>` for service tokens
- `role`: e.g. `STUDENT`, `SERVICE`
- `scopes`: fine-grained permissions (`cart:read`, `orders:manage`, etc.)
- `iss`: token issuer (`qprint-auth` by default)
- `aud`: token audience (`qprint-api` by default)
- `exp`: token expiry

## Environment variables
```
POSTGRES_URL=jdbc:postgresql://postgres:5432/qprint
POSTGRES_USER=qprint
POSTGRES_PASSWORD=...
REDIS_HOST=redis
REDIS_PORT=6379
KAFKA_BOOTSTRAP=kafka:9092
MINIO_URL=http://minio:9000
MINIO_ACCESS_KEY=...
MINIO_SECRET_KEY=...
JWT_SECRET=...
RAZORPAY_KEY_ID=...
RAZORPAY_KEY_SECRET=...
RAZORPAY_WEBHOOK_SECRET=...
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=...
SMTP_PASSWORD=...
SMTP_FROM=noreply@qprint.app
```

## Environment-separated JWT secrets
- Use separate env files and never reuse `JWT_SECRET` across environments:
    - Dev: `.env.dev` (create from `deploy/.env.dev.example`)
    - Staging: `deploy/.env.staging` (create from `deploy/.env.staging.example`)
    - Prod: `deploy/.env.prod` (create from `deploy/.env.prod.example`)
- Start environments with explicit env files:
    - Dev: `docker compose --env-file .env.dev up --build`
    - Staging/Prod: `docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.staging up -d` or `--env-file deploy/.env.prod`
- Exposure controls in `deploy/docker-compose.prod.yml`:
    - `GATEWAY_BIND_HOST=0.0.0.0` keeps gateway publicly reachable.
    - `FRONTEND_BIND_HOST=127.0.0.1` keeps frontend local-only by default (change only if intentionally public).
- Validate secret separation before deploy:
    - `./scripts/validate-jwt-secrets.ps1 -DevEnvPath .env.dev -StagingEnvPath deploy/.env.staging -ProdEnvPath deploy/.env.prod`

## API summary

### Auth (`/auth/**`)
- POST /auth/register
- POST /auth/verify-email
- POST /auth/resend-verification
- POST /auth/login
- POST /auth/refresh
- POST /auth/logout
- POST /auth/forgot-password
- POST /auth/reset-password
- GET /auth/me
- PUT /auth/profile
- PUT /auth/change-password

### Objects (`/api/objects/**`)
- POST /api/objects/upload
- GET /api/objects/{objectId}
- DELETE /api/objects/{objectId}

### Cart (`/api/cart/**`)
- GET /api/cart
- POST /api/cart/add
- DELETE /api/cart/item/{objectId}
- DELETE /api/cart
- GET /api/cart/count

### Checkout (`/api/checkout/**`)
- POST /api/checkout/initiate
- POST /api/checkout/webhook
- GET /api/checkout/status/{razorpayOrderId}

### Orders (`/api/orders/**`)
- POST /api/orders
- GET /api/orders
- GET /api/orders/active
- GET /api/orders/{orderId}
- GET /api/orders/active/{orderId}
- PATCH /api/orders/{orderId}/status

### Transactions (`/api/transactions/**`)
- GET /api/transactions
- GET /api/transactions/{id}

### Shops (`/api/shops/**`)
- GET /api/shops/nearby

## Kafka event flow
| Topic | Publisher | Consumer |
| --- | --- | --- |
| user.registered | Auth | - |
| user.verified | Auth | - |
| user.login | Auth | Cart |
| order.confirmed | Checkout | Orders |
| order.status.updated | Shops (Phase 2) | Orders |
| order.completed | Orders | Transactions |
| payment.failed | Checkout | - |

## Run services without Docker
- Each module is a Spring Boot app. Example:
    - `cd QPrint-auth`
    - `mvn spring-boot:run`
    - Repeat for any module

## Known limitations (Phase 1)
- Shops service is a stub returning a single hardcoded shop
- OTP service is internal-only and used for demo flows
- Cart warmup on login uses the cart_items table

## Phase 2 roadmap
- Shop operator portal
- Real-time order status updates from shops
- Multi-shop discovery and selection
- Live inventory and capacity tracking

## GitHub push readiness
This repo is prepared for GitHub with:
- CI workflow: `.github/workflows/ci.yml`
- Docker image publish workflow: `.github/workflows/publish-images.yml`
- Server deploy workflow: `.github/workflows/deploy.yml`
- Production compose stack: `deploy/docker-compose.prod.yml`
- Production env template: `deploy/.env.prod.example`
- Frontend container build: `QPrint-frontend/Dockerfile`

Initialize git locally (if needed):
```
git init -b main
git add .
git commit -m "Prepare QPrint for GitHub CI/CD"
git remote add origin https://github.com/<owner>/<repo>.git
git push -u origin main
```

## GitHub deployment setup

### 1) Configure repository variables
- `VITE_API_BASE_URL`: Public backend gateway URL used while building frontend container image.

### 2) Configure repository secrets
- `DEPLOY_HOST`: SSH host (VM/public server)
- `DEPLOY_PORT`: SSH port (set to `22` if default SSH port is used)
- `DEPLOY_USER`: SSH username
- `DEPLOY_SSH_KEY`: private SSH key for deploy user
- `GHCR_USERNAME`: GitHub username/org with package pull access
- `GHCR_TOKEN`: GitHub PAT with `read:packages`
- `DEPLOY_ENV_FILE`: full contents of your production `.env` (use `deploy/.env.prod.example` as base)

### 2.1) GHCR and CI secret rotation policy
- Rotate `GHCR_TOKEN` every 60 to 90 days, or immediately after any exposure.
- Use a fine-grained PAT with minimum scope (`read:packages` for deploy pull-only use).
- Never store GHCR token values in files inside this repo.
- Store deployment secrets only in GitHub Actions Secrets (or your cloud secret manager).
- Rotate `DEPLOY_SSH_KEY` if deploy machine access changed or team members changed.
- Update `DEPLOY_ENV_FILE` in GitHub Secrets after rotating app secrets (JWT, DB, SMTP, MinIO).
- Verify repository hygiene in CI: tracked real env files are blocked by `.github/workflows/ci.yml`.

### 3) Publish images
- Push to `main` (or run `Publish Docker Images` workflow manually).
- Images are pushed to `ghcr.io/<owner>/qprint-*`.

### 4) Deploy from GitHub
- Run `Deploy to Server` workflow from Actions.
- It copies `deploy/docker-compose.prod.yml` + `init.sql` to `~/qprint` on your server, writes `.env`, pulls GHCR images, and starts the stack.
