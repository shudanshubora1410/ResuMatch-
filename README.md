# ResuMatch

**Explainable resume screening.** Candidates see exactly why their resume matches a job;
recruiters screen applicants with ranked, explained results.

The match score is always computed by deterministic, rule-based Java that anyone can read and
audit. AI (Gemini / Groq) only explains, suggests, rewrites and summarises - **it never decides or
changes a score.**

> **Status: Phase 0 (foundation) complete.** The application builds, starts on Tomcat, connects to
> MySQL, runs its migrations and serves a health-checked API that the landing page reads live.
> Accounts, resumes, jobs, scoring and the AI layer arrive in Phases 1-16.

- Live frontend: Vercel (static `frontend/`)
- Live API: Render (Docker, Tomcat 11)
- Database: Aiven for MySQL 8
- Support: support.resumatch@gmail.com

---

## 1. What works today

| Area | Phase 0 state |
| --- | --- |
| Build | Maven WAR, Java 17 language level, Servlet 6.0 - runs on Tomcat 10.1 and Tomcat 11 |
| Config | `AppConfig` + `ConfigLoader` (OS environment over a git-ignored `.env`), fail-fast on missing secrets |
| Database | HikariCP pool (max 5), Flyway migrations at startup with retry/backoff |
| API | `GET /api/v1/health` with the standard success envelope |
| Errors | One central error handler producing the standard error envelope |
| Security | Security headers + CSP on every response, `SameSite=Lax`/`HttpOnly` session cookies, no secrets in Git |
| Files | `FileStorage` interface + `LocalDiskFileStorage` (UUID names, path-traversal safe) |
| Email | `MailSender` interface + `SmtpMailSender` (Gmail App Password); Brevo in Phase 2 |
| Frontend | Landing page, shared header/footer injected by one module, dark mode, live API status card |
| DevOps | Multi-stage `Dockerfile` (non-root), `docker-compose.yml`, `render.yaml`, CI that tests and builds the image |

## 2. Requirements

- **JDK 21** (Temurin). The project compiles for Java 17 - `maven.compiler.release=17`.
- **Maven 3.9.x**
- **MySQL Server 8.0** running on `localhost:3306`
- **Tomcat 11** (or Tomcat 10.1) extracted somewhere, e.g. `C:\apache-tomcat-11.0.27`
- **Eclipse IDE for Enterprise Java and Web Developers** (Maven project + Servers view)
- Docker is **not** needed locally. It is used only by Render and GitHub Actions.

## 3. Setup in 5 steps (Windows PowerShell)

### Step 1 - create the databases and a dedicated MySQL user

Do **not** let the application run as `root`.

```powershell
mysql -u root -p
```

```sql
CREATE DATABASE IF NOT EXISTS resumatch      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS resumatch_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'resumatch_app'@'localhost' IDENTIFIED BY 'choose-a-strong-password';
GRANT ALL PRIVILEGES ON resumatch.*      TO 'resumatch_app'@'localhost';
GRANT ALL PRIVILEGES ON resumatch_test.* TO 'resumatch_app'@'localhost';
FLUSH PRIVILEGES;
```

### Step 2 - create your `.env`

```powershell
cd C:\path\to\resumatch
Copy-Item .env.example .env
notepad .env      # set DB_PASSWORD, ADMIN_PASSWORD, and later SMTP_PASSWORD
```

`.env` is git-ignored. Only `.env.example` is committed, and it contains placeholders only.

### Step 3 - build

```powershell
cd C:\path\to\resumatch\backend
mvn clean package
```

You should see `BUILD SUCCESS` and `target\resumatch.war`.

### Step 4 - run it

**Option A - Eclipse (recommended while developing)**

1. `File > Import > Maven > Existing Maven Projects`, root directory `C:\path\to\resumatch\backend`.
2. Add Tomcat: `Window > Show View > Servers > New > Server > Tomcat v11.0 Server`,
   point it at your Tomcat folder, and add the `resumatch` module.
3. Tell Tomcat where your `.env` is. Two forms, both supported by `ConfigLoader`, so pick
   whichever fits how you start the server:

   ```text
   Environment variable : RESUMATCH_ENV_FILE=C:\path\to\resumatch\.env
   VM argument (-D)     : -DRESUMATCH_ENV_FILE=C:\path\to\resumatch\.env
   Named -D form        : -Dresumatch.env.file=C:\path\to\resumatch\.env
   ```

   In Eclipse: `Run > Run Configurations > Apache Tomcat > Arguments > VM arguments` for the
   `-D` form, or the `Environment` tab for the variable. Without one of these, Tomcat looks for
   `.env` in `$CATALINA_HOME\bin` and startup fails with `Required configuration value is
   missing: DB_URL`.

4. The frontend must be reachable at the context root, because the pages call `/api/v1/...`:
   `Servers` view > double-click Tomcat > `Modules` tab > select `resumatch` > `Edit...` > Path: `/`.
5. Start the server and open <http://localhost:8080>.

If the page loads but `/api/v1/health` 404s in Eclipse, the `../frontend` folder was not copied
into the deployment. Either run `mvn package` and deploy the WAR (Option B), or add it manually:
`Project > Properties > Deployment Assembly > Add > Folder > frontend`.

**Option B - drop the WAR into Tomcat**

```powershell
# Deploy as ROOT so the pages and the API share one origin
Remove-Item -Recurse -Force "C:\apache-tomcat-11.0.27\webapps\ROOT" -ErrorAction SilentlyContinue
Copy-Item "target\resumatch.war" "C:\apache-tomcat-11.0.27\webapps\ROOT.war" -Force

# Environment variables for that Tomcat session
$env:DB_URL="jdbc:mysql://localhost:3306/resumatch?characterEncoding=utf8&allowPublicKeyRetrieval=true&useSSL=false"
$env:DB_USER="resumatch_app"
$env:DB_PASSWORD="choose-a-strong-password"

& "C:\apache-tomcat-11.0.27\bin\startup.bat"
```

### Step 5 - check it

```powershell
curl.exe http://localhost:8080/api/v1/health
```

Expected shape:

```json
{
  "success": true,
  "data": {
    "status": "UP",
    "version": "1.0.0-SNAPSHOT",
    "environment": "local",
    "uptimeSeconds": 12,
    "database": { "status": "UP", "latencyMs": 3, "checkedAt": "2026-11-02T09:15:04Z" },
    "migrations": { "status": "SUCCEEDED" },
    "storage": "disk",
    "mailProvider": "smtp"
  }
}
```

`status` is `UP` only when the database answers **and** the migrations succeeded; otherwise it is
`DEGRADED` and the HTTP status stays 200 so Render does not restart the service in a loop while
Aiven's free database is waking up.

## 4. Running the tests

```powershell
cd C:\path\to\resumatch\backend
mvn test                                    # unit tests
mvn verify                                  # tests + JaCoCo coverage report
start target\site\jacoco\index.html         # open the coverage report
```

Phase 0 has **69 tests** (64 unit + 5 MySQL integration) at **85% line coverage**. Unit tests need
no database; the integration tests connect to a real MySQL schema and are *skipped* (not failed)
when it is not configured, so `mvn test` still works on a fresh clone.

```powershell
# Run the MySQL integration tests too (PowerShell)
$env:TEST_DB_URL="jdbc:mysql://localhost:3306/resumatch_test?characterEncoding=utf8&allowPublicKeyRetrieval=true&useSSL=false"
$env:TEST_DB_USER="resumatch_app"
$env:TEST_DB_PASSWORD="choose-a-strong-password"
mvn verify
```

Tests never need Docker.

## 5. Environment variables

Every value comes from the environment; `.env` only exists as a convenience for local runs.

| Variable | Required | Purpose |
| --- | --- | --- |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | yes | JDBC URL (Aiven needs `sslMode=REQUIRED`), user and password |
| `DB_POOL_MAX` | no (5) | Hikari pool size - keep small on Aiven's free plan |
| `TEST_DB_URL`, `TEST_DB_USER`, `TEST_DB_PASSWORD` | tests only | Test database connection |
| `PORT` | Render sets it | Port Tomcat listens on inside the container |
| `APP_ENV`, `APP_NAME` | no | Shown in `/health` and logs |
| `FRONTEND_ORIGIN` | no | Only used by the optional CORS filter (off by default) |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | from Phase 1 | Admin account seeded on first start |
| `SUPPORT_EMAIL` | no | Defaults to support.resumatch@gmail.com; used in emails and the footer |
| `FILE_STORAGE` | no (disk) | `disk` locally, `db` in production |
| `FILE_STORAGE_DIR` | no (`uploads`) | Folder for disk storage, outside the web root |
| `MAIL_PROVIDER` | no (smtp) | `smtp` locally, `brevo` in production |
| `MAIL_FROM`, `MAIL_FROM_NAME` | with SMTP | Sender identity |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD` | with SMTP | Gmail App Password for development |
| `BREVO_API_KEY` | with Brevo | Brevo HTTPS API key (Phase 2) |
| `GOOGLE_CLIENT_ID` | Phase 2 | Google Identity Services client id |
| `GEMINI_API_KEY`, `GEMINI_MODEL`, `GROQ_API_KEY`, `GROQ_MODEL` | Phase 11 | AI providers (server side only) |
| `RESUMATCH_ENV_FILE` | no | Absolute path to a `.env` file when Tomcat's working directory is elsewhere |

## 6. Project layout

```
resumatch/
  backend/           Maven WAR: servlet -> service -> dao -> MySQL (later phases); common/* today
  frontend/          Static site for Vercel; also copied into the WAR at build time
    vercel.json      Rewrites /api/* to the Render backend (one origin, first-party cookies)
  docker/            Container entrypoint (honours $PORT)
  Dockerfile         Multi-stage build, runs Tomcat 11 as a non-root user
  docker-compose.yml MySQL + app for anyone with Docker
  render.yaml        Render blueprint
  docs/              Phase 0 plan, ER diagram, API list, deployment checklist
  .github/workflows/ci.yml   Build, test, coverage, Docker image build
```

## 7. Deployment (Aiven + Render + Vercel)

The step-by-step checklist is in [`docs/phase-0-plan.md`](docs/phase-0-plan.md#7-deployment-checklist-run-this-before-phase-1).
Short version:

1. **Aiven**: create a free MySQL service; the JDBC URL needs `sslMode=REQUIRED` and a non-default port.
2. **Render**: deploy the `Dockerfile` as a free web service, region Singapore, health check
   `/api/v1/health`, and set the secrets in the dashboard (never in the repo).
3. **Vercel**: import the repo with root directory `frontend/`, then replace
   `REPLACE_WITH_YOUR_RENDER_URL` in `frontend/vercel.json` with your Render host.
4. Open the Vercel URL - the status card should read `UP`, proving Vercel -> Render -> Aiven.

Free-tier realities the code already handles: Render sleeps after idle (the frontend retries and
explains the wait), Render's disk is ephemeral (files go to the database from Phase 3), Render
blocks SMTP ports (Brevo HTTPS from Phase 2), and Aiven can be powered off (startup retries and
`/health` reports `DEGRADED` instead of crashing).

## 8. Known limitations (Phase 0)

- No accounts yet: registration, login, CSRF tokens and role checks arrive in Phase 1. The
  `X-CSRF-Token` header is already wired in `api.js`, ready for the token endpoint.
- `FILE_STORAGE=db` and `MAIL_PROVIDER=brevo` fall back to disk/no-op with a warning until Phases
  3 and 2 respectively.
- Only `/api/v1/health` exists; every other URL under `/api/` returns the standard JSON 404.
- The Docker image is built by CI, not on the development PC (Docker is not installed there).

## 9. Roadmap

Phases 1-16: accounts and security, Google login and recovery, resume pipeline, skill engine,
jobs, the scoring engine, dynamic rescoring and dashboards, applications and email, recruiter
tools, resume quality reports, the AI layer, candidate AI, recruiter AI, admin analytics, polish,
and the docs/viva pack.

---

© 2026 ResuMatch. Built by [Shudanshu Bora](https://github.com/shudanshubora1410) ·
[LinkedIn](https://linkedin.com/in/shudanshubora) · support.resumatch@gmail.com
