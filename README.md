# Common Goods e-commerce application

Full-stack e-commerce monorepo: React storefront, Spring Boot REST API, and
MySQL persistence. This implementation includes customer/admin accounts,
catalog and inventory management, persistent carts, Cash on Delivery checkout,
order history, and a protected admin dashboard. Online card processing is
intentionally not simulated; checkout labels Cash on Delivery clearly.

## Technology

- Java 21 LTS, Spring Boot 3.5.7, Maven 3.9.9 wrapper
- React 19, Vite 7, React Router 7, Axios 1, Bootstrap 5
- MySQL 8.0+ and Flyway migrations
- Spring Security, BCrypt, short-lived JWT access tokens, rotating hashed
  refresh tokens in HttpOnly cookies, request validation, and OpenAPI

## Layout

```text
backend/    Spring Boot API, migrations, tests, Dockerfile, Maven wrapper
frontend/   React/Vite responsive application and Vercel SPA configuration
database/   MySQL Workbench and migration notes
docs/       Architecture, ER model, and deployment runbook
```

See [docs/architecture.md](docs/architecture.md) for the ER diagram and
[docs/deployment.md](docs/deployment.md) for deployment guidance.

## Requirements

- Java 21
- Node.js 22.12+ and npm
- MySQL Server 8.0+ / MySQL Workbench
- PowerShell on Windows (commands below)

Maven is downloaded by the committed wrapper on first use; a global Maven
installation is not required.

## 1. Create a local MySQL database

In MySQL Workbench, create an application database and a dedicated local user.
Keep the password private:

```sql
CREATE DATABASE ecommerce
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'ecommerce_app'@'localhost' IDENTIFIED BY 'use-a-unique-local-password';
GRANT ALL PRIVILEGES ON ecommerce.* TO 'ecommerce_app'@'localhost';
```

Flyway applies `V1__create_initial_schema.sql` automatically when the backend
starts. Hibernate validates the schema; it does not recreate production tables.
See [database/README.md](database/README.md).

## 2. Run the backend

From `backend` in PowerShell, set the database credentials and a fresh random
JWT secret (the secret is used only by this shell):

```powershell
$env:DB_USERNAME = "ecommerce_app"
$env:DB_PASSWORD = "your-local-database-password"
$env:JWT_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
$env:SEED_DEMO_DATA = "true"
.\mvnw.cmd spring-boot:run
```

The optional demo seeder adds sample categories and products only when
`SEED_DEMO_DATA=true` and the product table is empty. Do not enable it in
production. Check `http://localhost:8080/actuator/health` and open
`http://localhost:8080/swagger-ui.html` for live API documentation.

### Bootstrap the first administrator

Set both variables before the first backend startup. Choose an email that has
not been registered and a unique password of at least 16 characters:

```powershell
$env:ADMIN_BOOTSTRAP_EMAIL = "store-admin@example.com"
$env:ADMIN_BOOTSTRAP_PASSWORD = "use-a-unique-strong-password-here"
```

The backend hashes the password and assigns ADMIN. Repeated starts are safe
after that account exists; an existing CUSTOMER account is never promoted.
Public registration always assigns CUSTOMER.

## 3. Run the storefront

From `frontend` in another PowerShell terminal:

```powershell
npm install
npm run dev
```

Open the Vite URL, normally `http://localhost:5173`. Vite proxies `/api` to
`http://localhost:8080`; set `VITE_API_BASE_URL` when the API is on another
origin. The browser keeps access tokens in memory and never stores refresh
tokens in local storage.

## Features and API

| Area | API routes |
|---|---|
| Auth/profile | `/api/v1/auth/*`, `/api/v1/me`, `/api/v1/me/password` |
| Catalog | `GET /api/v1/products`, `/products/{slug}`, `/categories` |
| Cart/addresses | `/api/v1/cart`, `/api/v1/addresses` |
| Orders | `/api/v1/orders`, `/orders/checkout`, `/orders/{id}` |
| Reviews | `GET /api/v1/products/{slug}/reviews`, `POST /api/v1/reviews` |
| Admin | `/api/v1/admin/dashboard`, `/products`, `/categories`, `/inventory`, `/orders`, `/customers` |
| Health/docs | `/actuator/health`, `/api/v1/health`, `/swagger-ui.html` |

Checkout accepts only `CASH_ON_DELIVERY`; the backend recalculates prices,
serializes stock changes with database locks, snapshots order lines, and
supports per-customer idempotency keys. Orders cannot oversell stock. Legal
order status transitions and customer ownership are enforced server-side.

## Tests and production

Run the backend tests (health, H2-backed auth/authorization, checkout pricing,
idempotency, stock decrement, and order ownership):

```powershell
cd backend
.\mvnw.cmd test
```

Build the frontend:

```powershell
cd frontend
npm run build
```

The backend [Dockerfile](backend/Dockerfile) runs as a non-root user. The
frontend contains a Vercel SPA rewrite at [vercel.json](frontend/vercel.json).
For hosted MySQL, production profiles, CORS, cookies, secrets, and launch steps,
follow [docs/deployment.md](docs/deployment.md). A real deployment requires
your own hosting accounts, domain, and environment credentials; none are
committed in this repository. A live MySQL migration/connection and cloud
deployment have not yet been verified.
