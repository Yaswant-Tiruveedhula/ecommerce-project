# Production deployment

The repository is deployable as a static Vite site plus a Dockerized Java API
and managed MySQL. The backend image is intentionally provider-neutral so the
operator can select a container host with suitable region, network egress,
availability, and budget. A real production deployment still requires cloud
accounts, a domain, and secrets that must not be added to source control.

## Beginner deployment walkthrough

This walkthrough uses **GitHub** to store the project, **Aiven** for managed
MySQL, **Render** for the Spring Boot API, and **Vercel** for the React website.
The same repository is connected to Render and Vercel; do not upload individual
source files to either hosting dashboard.

You will need a GitHub account, accounts with the hosting providers, and a
domain name. Hosting plans, database networking options, and prices change;
check each provider's current limits and choose plans that support your
availability and security requirements. For reliable login cookies, use two
HTTPS subdomains of your own domain, such as `shop.example.com` and
`api.example.com`. The default Vercel and Render domains are on different
sites, which can cause browsers to block the refresh cookie.

### 1. Upload the project to GitHub

From VS Code, open **Terminal > New Terminal**, select **PowerShell**, and run
these commands from the project root (`D:\Training\FullStack\EC`). First,
create an empty GitHub repository named `common-goods` in the GitHub website.
Do not add a README or license there; this project already contains its files.
Replace `YOUR-GITHUB-USERNAME` below with your GitHub username:

```powershell
git init
git branch -M main
git add .
git status
```

Review `git status`: it must not list `.env` files, passwords, database
credentials, or other secrets. The repository's `.gitignore` excludes common
local build outputs and `.env` files. Then publish the project:

```powershell
git commit -m "Initial deployment"
git remote add origin https://github.com/YOUR-GITHUB-USERNAME/common-goods.git
git push -u origin main
```

If Git asks, sign in through the browser or Git Credential Manager; do not put
a GitHub password or access token in a command. Keep the repository private
unless you intentionally want the source code public.

For future code changes, commit and push them from this same VS Code terminal:

```powershell
git add .
git commit -m "Describe your change"
git push
```

After the hosting platforms are connected to the repository, pushing to `main`
automatically starts a new frontend and backend deployment. You do not need to
upload the files again or manually run build commands on the hosting websites.

### 2. Create the production database on Aiven

1. In the Aiven console, create a **MySQL 8.x** service in a region close to
   the backend. Review the current plan, backups, storage, and network options.
2. In the service's **Databases** and **Users** area, create a database named
   `ecommerce` and a dedicated application user/password. Do not use the
   database administrator account for the app if the service allows a
   restricted user.
3. Keep the service's host, port, username, and password available for the
   Render environment setup in the next step. Do not put these in GitHub or
   the Vercel project.
4. Require encrypted MySQL connections. Restrict database access to Render
   using private networking or the selected Render plan's outbound IP ranges
   in the database provider's allowlist. Never allow database connections
   from `0.0.0.0/0`.

Flyway creates or updates the application's tables when the API starts.
Do not manually import the migration SQL files.

### 3. Deploy the API on Render

1. In Render, choose **New > Web Service**, connect GitHub, and select the
   `common-goods` repository.
2. Set **Root Directory** to `backend` and choose **Docker** as the runtime.
   The Dockerfile is already at `backend/Dockerfile`; Render builds it from
   this folder. Leave the start command empty so the Dockerfile's `ENTRYPOINT`
   starts Spring Boot.
3. Set the health check path to `/actuator/health`.
4. Add these variables in the Render service's **Environment** settings. Use
   the Aiven connection details from step 2 and a generated secret; enter
   secret values directly in Render, never into source files:

   | Key | Value |
   |---|---|
   | `SPRING_PROFILES_ACTIVE` | `prod` |
   | `DB_HOST` | Aiven host |
   | `DB_PORT` | Aiven port |
   | `DB_NAME` | `ecommerce` |
   | `DB_USERNAME` | Dedicated Aiven application user |
   | `DB_PASSWORD` | That user's password |
   | `DB_SSL_MODE` | `REQUIRED` |
   | `JWT_SECRET` | New random secret; see the PowerShell command below |
   | `APP_COOKIE_SECURE` | `true` |
   | `APP_COOKIE_SAME_SITE` | `Lax` |
   | `FRONTEND_URL` | `https://shop.example.com` (replace with your storefront domain) |
   | `STORE_CURRENCY` | `USD` (or your chosen ISO currency code) |
   | `SEED_DEMO_DATA` | `false` |

   Render supplies the `PORT` variable for its web service; the app reads it
   automatically. Do not set `VITE_*` variables on the backend.
5. To generate a fresh JWT secret locally, run this in PowerShell, then copy
   the printed value into Render's `JWT_SECRET` field:

   ```powershell
   $bytes = New-Object byte[] 48
   $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
   $rng.GetBytes($bytes)
   [Convert]::ToBase64String($bytes)
   $rng.Dispose()
   ```

6. For the first deployment only, also set `ADMIN_BOOTSTRAP_EMAIL` and
   `ADMIN_BOOTSTRAP_PASSWORD` in Render to an unused email and a unique
   16–72-character password. The API creates this admin on startup. Remove
   both variables after confirming that the admin can sign in.
7. Click **Create Web Service**. Render checks out the files from GitHub,
   builds the Docker image, and runs its start command automatically. Watch
   the **Logs** tab. Wait for Flyway to finish and the service health check to
   pass before proceeding.

### 4. Deploy the website on Vercel

1. In Vercel, choose **Add New > Project**, import the same GitHub repository,
   and set **Root Directory** to `frontend`.
2. Use these build settings:
   - Framework preset: **Vite**
   - Build command: `npm run build`
   - Output directory: `dist`
3. Add these environment variables in **Project Settings > Environment
   Variables** for Production:

   | Key | Value |
   |---|---|
   | `VITE_API_BASE_URL` | `https://api.example.com/api/v1` (replace with your API domain) |
   | `VITE_STORE_CURRENCY` | The same currency as the backend, for example `USD` |

   These values are public frontend configuration. Never add database
   passwords, `JWT_SECRET`, or other private credentials to `VITE_*` variables.
4. Click **Deploy**. Vercel checks out the frontend folder, runs the build, and
   publishes `dist`. The checked-in `vercel.json` also configures routes such
   as `/products` to load correctly when opened directly.
5. Add your storefront domain in Vercel's **Domains** settings and follow its
   DNS instructions. Add the API subdomain in Render's **Custom Domains**
   settings and follow Render's DNS instructions. Wait until both domains show
   HTTPS/TLS as active.
6. Return to Render and set `FRONTEND_URL` to the exact storefront origin,
   including `https://` and with no trailing slash, for example
   `https://shop.example.com`. Save the change and let Render redeploy.
7. If you change a Vercel environment variable, trigger a new Vercel
   deployment; environment values are embedded at frontend build time.

### 5. Verify the live site

Replace `api.example.com` and `shop.example.com` with your actual domains:

1. Open `https://api.example.com/actuator/health`; the response should report
   `"status":"UP"`.
2. Open `https://shop.example.com`. Check that products and their images load.
3. Register a customer, sign in, reload the page, and verify that the session
   still works.
4. Sign in with the bootstrapped admin and add a category/product. Then test
   the cart and Cash on Delivery checkout as a customer.
5. Inspect Render logs for Flyway or database errors. In browser developer
   tools, check **Console** and **Network** for CORS, cookie, or failed API
   requests.

### Where commands run, and what runs automatically

| Action | Where it runs |
|---|---|
| `git init`, `git add`, `git commit`, `git push` | Your local VS Code PowerShell terminal, opened at the project root |
| `.\mvnw.cmd clean verify` | Your local PowerShell terminal after `cd backend` (optional pre-deploy test) |
| `npm run build` | Your local terminal after `cd frontend` (optional pre-deploy test) |
| `npm run build` for deployment | Vercel, when you deploy or push a change |
| Docker image build and container startup | Render, when you deploy or push a change |
| Flyway schema migrations | Automatically by the API when it starts |
| Health check | Render requests `/actuator/health` automatically |

The hosting dashboard's **Environment Variables** fields are configuration
inputs, not a command prompt. Run commands in VS Code's PowerShell terminal;
the platform runs only the configured build/start steps. To test before
publishing, use:

```powershell
Set-Location D:\Training\FullStack\EC\backend
.\mvnw.cmd clean verify

Set-Location D:\Training\FullStack\EC\frontend
npm run build
```

## Suggested hosting shape

- **Frontend:** Vercel static hosting, with the project root set to `frontend`.
  The checked-in `frontend/vercel.json` sends client-side routes to `index.html`.
- **Backend:** a Java 21-capable service that deploys a Docker image, supports
  HTTPS/custom domains, environment variables, health checks, and outbound
  access to the database.
- **Database:** Aiven for MySQL is one managed MySQL option. Its current product
  page advertises MySQL 8.4; its Developer plan is listed at $5/month for
  test/personal projects, not as a production sizing recommendation. Check
  region, storage, backups, networking, SLA, and current pricing before
  choosing a production plan: [Aiven for MySQL](https://aiven.io/mysql).

Keep the browser and API on the same site, for example
`shop.example.com` and `api.example.com`. This lets the refresh cookie use
`SameSite=Lax` while remaining host-only to the API. If separate registrable
domains are unavoidable, `SameSite=None; Secure` is available via
`APP_COOKIE_SAME_SITE=None`, but browser third-party-cookie restrictions can
break refresh sessions; test supported browsers before launch.

## Build and publish the backend image

From `backend`, run the test suite and build the executable JAR:

```powershell
.\mvnw.cmd clean verify
```

Build and publish the container to the selected registry:

```powershell
docker build -t YOUR_REGISTRY/common-goods-api:VERSION .
docker push YOUR_REGISTRY/common-goods-api:VERSION
```

Configure the container service to listen on its supplied `PORT` (defaults to
8080), terminate HTTPS at the platform, and use `/actuator/health` as its
health-check path. Do not expose MySQL to the public internet; use private
networking or a database IP allowlist restricted to the backend's egress.

## Managed MySQL setup

1. Create a MySQL 8.x service in the deployment region nearest the API.
2. Create the `ecommerce` database and an application-specific DB user with
   permissions only on that database.
3. Keep the provider's host, port, username, and password in the backend
   service's secret/environment manager.
4. Require TLS with `DB_SSL_MODE=REQUIRED` (use a stricter verification mode if
   the provider documents the required trust configuration).
5. Restrict inbound database networking to the backend. Do not use a public
   `0.0.0.0/0` allowlist.
6. Flyway applies the versioned schema on API startup. Inspect migration status
   in logs before routing live traffic.

## Backend environment

Set these on the **backend only**:

| Variable | Required value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `PORT` | Platform-provided HTTP port |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | Private managed-MySQL endpoint |
| `DB_USERNAME`, `DB_PASSWORD` | Dedicated application DB user |
| `DB_SSL_MODE` | `REQUIRED` or provider's stronger supported mode |
| `JWT_SECRET` | New random secret, at least 32 UTF-8 bytes |
| `FRONTEND_URL` | Exact HTTPS storefront origin; comma-separated only for additional approved origins |
| `APP_COOKIE_SECURE` | `true` |
| `APP_COOKIE_SAME_SITE` | `Lax` for same-site subdomains; use `None` only if required |
| `STORE_CURRENCY` | Three-letter ISO 4217 currency code |
| `ADMIN_BOOTSTRAP_EMAIL` | New, unused administrator email for first provisioning |
| `ADMIN_BOOTSTRAP_PASSWORD` | Unique password, 16–72 characters |
| `SEED_DEMO_DATA` | `false` |

Generate a signing secret outside the repository. For example, in PowerShell:

```powershell
$bytes = New-Object byte[] 48
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
$rng.Dispose()
```

The bootstrap creates a new administrator only when the configured email is
unused. It is safe to keep the same bootstrap values for restarts after the
administrator exists; an existing CUSTOMER account causes startup to fail
rather than being silently promoted. After initial provisioning and
verification, remove both bootstrap variables from the deployment environment.
Resetting an administrator password is an operational database procedure and
must preserve the BCrypt hash format.

Set the same `FRONTEND_URL` to the exact origin, such as
`https://shop.example.com`, not a wildcard. The API uses credentialed CORS and
CSRF tokens for state-changing requests.

## Frontend deployment

1. Create a Vercel project from the repository.
2. Set its project/root directory to `frontend`.
3. Set build command `npm run build` and output directory `dist`.
4. Set `VITE_API_BASE_URL=https://api.example.com/api/v1`.
5. Set `VITE_STORE_CURRENCY` to the same ISO currency code as backend
   `STORE_CURRENCY`.
6. Configure the custom storefront domain and HTTPS.
7. Add the exact storefront origin to backend `FRONTEND_URL`.
8. Verify the API host sends credentialed CORS headers and the browser accepts
   the API's secure, HttpOnly refresh cookie.

`VITE_*` values are public build-time configuration; never put passwords,
database URLs containing credentials, or JWT secrets there.

## Deployment and smoke-test checklist

1. Verify the API's `/actuator/health` reports `UP` and that the backend log
   shows Flyway completed successfully.
2. Open `/swagger-ui.html` on the API domain and confirm the API documentation
   loads over HTTPS.
3. Register a customer, sign out, sign in, and verify refresh works after a
   page reload.
4. Sign in as the bootstrapped admin; create a category and a product.
5. As a customer, search/filter products, add an item to the bag, and update
   its quantity.
6. Save a delivery address, place a Cash on Delivery order, and verify the
   order snapshot and stock decrement.
7. Attempt checkout from two sessions with limited stock and confirm stock
   never goes negative.
8. Progress the order through the valid admin status workflow and confirm the
   customer can only see their own account, cart, and orders.
9. Confirm no request, page, or database record contains raw passwords,
   access tokens beyond the active browser session, or card data.
10. Test the final HTTPS domains and the production browser set, including
    cookie refresh and CORS.

Payment remains Cash on Delivery. The application does not claim that a card
payment was made and does not store payment credentials. Add a gateway only
after implementing signed server-side verification/webhooks and corresponding
tests.

## Operations and troubleshooting

- **Flyway cannot connect:** confirm the DB endpoint/port, TLS mode, firewall,
  user grants, and secret values. Never work around this by disabling Flyway in
  production.
- **Hibernate schema validation fails:** preserve the database; compare the
  Flyway history and current migration against the deployed schema, then add a
  new migration rather than using `ddl-auto=update`.
- **Refresh fails in the browser:** check that frontend/API are HTTPS, CORS
  uses the exact frontend origin with credentials, the cookie is present, and
  CSRF token fetch succeeds. Prefer same-site custom subdomains.
- **403 on a mutation:** fetch `/api/v1/auth/csrf` and send the returned token
  in `X-XSRF-TOKEN`; check the CORS allow-list.
- **No products locally:** set `SEED_DEMO_DATA=true` only in a local
  environment, or add products through the admin UI.
- **Database outage:** `/actuator/health` includes the database health check.
  Keep the database private and configure provider backups/restore testing.
