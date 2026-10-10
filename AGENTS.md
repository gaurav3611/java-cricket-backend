# AGENTS.md

Guidance for AI coding agents working in this repository. Humans should start with [README.md](README.md).

## What this is

The REST API for SwingLab, a cricket bat swing analytics app. It is written in Java 21 with Spring Boot 4.1 and built with Maven.

It is a rewrite of the Flask backend in the [Cricket](https://github.com/gaurav3611/Cricket) repository, `backend/app.py` at commit `bc4ea4b`. It must keep giving the same answers as that backend. Most of the rules below exist to protect that.

Status: it builds, passes its tests and runs locally. It is not deployed. It has never been run against a real Firebase project. This repository is public.

## Read this first

1. **Java 21 must be the active JDK.** On the owner's Mac the default `java` is 11, and the build then fails with `release version 21 not supported`. Set this first:

   ```bash
   export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
   ```

2. **A recording of the Flask backend is the contract.** `src/test/resources/parity/golden.json` holds 454 requests and the answers Flask gave. `ParityTest` replays them. Never edit that file by hand, and never change an expected value to make a test pass.
3. **The page at `/docs` is checked against the code.** It shows what is in `src/main/resources/static/docs/endpoints.json`. Tests fail if that file and the controllers disagree. Adding or changing an endpoint or a response means updating that file too. See [The API reference page](#the-api-reference-page).
4. **Two versions of Jackson are on the classpath.** Use Jackson 3, whose classes start with `tools.jackson`. The `com.fasterxml.jackson.databind` classes are Jackson 2, present only because the Firebase SDK needs them. They compile, but they are not the mapper that Spring configures for this app.
5. **This repository is public.** Never commit `.env`, `serviceAccountKey.json`, a real token, or anything copied from a real Firebase project.

## Commands

Run all of these from the repository root, with `JAVA_HOME` set as above.

```bash
./mvnw test                          # the whole suite, a few seconds once libraries are downloaded
./mvnw test -Dtest=ParityTest        # one test class
./mvnw spring-boot:run               # start on http://localhost:5050
./mvnw package                       # build target/swinglab-api-0.0.1-SNAPSHOT.jar
PORT=5051 ./mvnw spring-boot:run     # another port
```

- `./mvnw spring-boot:run` works without a terminal attached, so it is safe to start as a background job. Stop it with `pkill -f com.swinglab.api.SwinglabApiApplication`.
- With no Firebase key the API starts in demo mode and logs a warning. That is the normal local state, not an error.
- Check a running instance with `curl http://localhost:5050/api/health`, or open `http://localhost:5050/docs`.
- Start it from the repository root. The `.env` file and a relative `FRONTEND_DIR` are resolved from the start directory.

## Code map

Everything is under `src/main/java/com/swinglab/api/`.

| Package | Holds |
|---|---|
| `auth` | `@RequireAuth`, `AuthInterceptor`, the `TokenVerifier` interface |
| `config` | `WebConfig`: CORS, JSON-only answers, the `/docs` redirect, interceptor registration |
| `demo` | `POST /api/demo/start` and `DemoSimulator` |
| `error` | `ApiException` and `GlobalExceptionHandler` |
| `firebase` | Every class that touches the Firebase SDK, and demo mode detection in `FirebaseContext` |
| `health` | `GET /api/health` |
| `profile` | `GET` and `PUT /api/profile` |
| `sensor` | `POST /api/sensor-data`, `GET /api/esp32/status`, per-reading formulas in `SensorMath` |
| `session` | Recording, end-of-session scores in `SessionAnalytics`, stored history |
| `util` | `PyMath` for Python-style rounding, `Timestamps` for Python-style time text |

Other places that matter:

| Path | Holds |
|---|---|
| `src/main/resources/application.properties` | Port, JSON naming, Firebase settings, static file locations |
| `src/main/resources/static/docs/` | The API reference page and its endpoint list |
| `src/test/resources/parity/` | `golden.json` and the script that records it |
| `src/test/resources/frontend-fixture/` | Three small files that stand in for a frontend in `ParityTest` |

## Rules that keep the answers identical to Flask

- **Formulas are copied step for step.** `SensorMath` and `SessionAnalytics` keep the constants, the order of operations and the rounding of the Python code. Do not simplify or reorder the arithmetic.
- **Round with `PyMath`, never `Math.round`.** Python rounds exact ties to the even digit, so 45.25 becomes 45.2.
- **Format times with `Timestamps.isoUtc`.** Stored sessions are sorted by this text, so the format must not change.
- **JSON keys are snake_case.** Records get them through `spring.jackson.property-naming-strategy=SNAKE_CASE`. Maps are written with literal keys.
- **Every error is `{"error": "..."}`.** Throw `ApiException` with a status and message. `GlobalExceptionHandler` shapes the rest.
- **Every answer is JSON, whatever the `Accept` header says.** `WebConfig.configureContentNegotiation` enforces this. Without it, browsers get XML, because the Firebase SDK brings an XML library.
- **`POST /api/sensor-data` reads its body as text and parses it itself.** It must keep accepting any `Content-Type`, because the bat firmware is not available to check what it sends.
- **Some odd behavior is deliberate.** The sensor endpoint answers `500` for input it cannot parse. The sweet spot percentage is not capped at 100. The server-side demo simulation produces speeds near zero. All three match Flask. The README lists the rest.

To change behavior on purpose, change the Flask backend as well and record `golden.json` again, or add the difference to the table in the README and adjust the tests to state the new behavior. Do not let the two drift silently.

## Sign-in, demo mode and Firebase

- Mark a controller method with `@RequireAuth` to require a Firebase ID token. The caller's uid then arrives as `@RequestAttribute(AuthInterceptor.UID)`.
- Demo mode is on only when no key is configured. Every caller is then `demo-user` and nothing is stored. Services check `firebase.isDemoMode()` before touching storage.
- A key that is present but unreadable must stop startup. Never fall back to demo mode, because that would switch sign-in checks off.
- Only the `firebase` package may import Firebase classes. Other code uses the interfaces `TokenVerifier`, `UserDirectory`, `SessionRepository` and `UserProfileRepository`. This is what lets the tests replace Firebase.
- Check ownership on every stored document. A session belongs to the uid in its `uid` field.

## State

Sessions in progress and the device list are kept in memory, in `SessionService` and `SensorService`. Readings arrive on several threads, so `ActiveSession` is synchronized and the device list is a synchronized map that keeps insertion order. The API must run as a single instance, and a restart loses this state. Do not add a second copy of this state elsewhere.

## The API reference page

`/docs` redirects to `static/docs/index.html`, a single self-contained page with no outside scripts or styles. It reads `static/docs/endpoints.json` and shows every endpoint with every response it can give. Its Send buttons call the running API.

- **The endpoint list must match the controllers.** `ApiDocsTest` compares the two in both directions.
- **Every documented response is replayed.** Each response in `endpoints.json` has a `verify` block. `"in": "demo"` examples are sent to the API in demo mode by `ApiDocsTest`. `"in": "secured"` examples are produced by `ApiDocsSecuredTest`, which sets up the stand-ins for each `case`.
- **To add an endpoint:** write the controller method, add the endpoint with all its responses to `endpoints.json`, and give each response a `verify` block. A new `secured` case also needs an arrangement in `ApiDocsSecuredTest.arrange`. The test fails with a message naming the missing case.
- **Copy examples from real output.** Run the request and paste the answer. Ids and timestamps listed in `volatile_keys` may differ, everything else must match exactly.
- **Build the page's elements with `textContent`, never `innerHTML`.** The page shows text that comes from API answers.
- **No test opens the page in a browser.** After editing `index.html`, open `/docs`, press several Send buttons and run the demo recording. Check it at phone width and in dark mode too.

Static files are looked up in the frontend folder first, then in `src/main/resources/static`. The small `static/index.html` sends `/` to `/docs` when no frontend supplies its own start page.

## Tests

| Test | Proves |
|---|---|
| `ParityTest` | The answers match the 454 recorded Flask answers |
| `SecuredModeTest` | Token checks, owner-only access, storage calls and storage failures |
| `ApiDocsTest`, `ApiDocsSecuredTest` | The reference page lists the real endpoints and shows real answers |
| `FirebaseWiringTest`, `FirebaseContextTest` | The real Firebase SDK starts with a generated key, rejects bad tokens, and a broken key stops startup |
| `SensorMathTest`, `PyMathTest` | Formulas and rounding match values computed in Python |

- Tests start the real server on a random port and call it over HTTP with `TestHttp`. Compare JSON with `JsonCompare`.
- Tests must not need a Firebase project, real credentials or another repository.
- A new test passing the first time proves little. Break the code on purpose once and confirm the test fails.
- Recording `golden.json` again needs the Cricket repository cloned beside this one and its Python dependencies. The README has the command.

## Conventions

- Records for data, constructor injection for dependencies, no field injection, no Lombok.
- Four spaces, one class per file, `final` utility classes with a private constructor.
- Comments say why, especially where code mirrors Flask or keeps a quirk on purpose.
- Log through SLF4J with the tags the Flask app used: `[OK]`, `[ERR]`, `[AUTH]`, `[DB]`, `[WARNING]`.
- Avoid new dependencies. The Firebase SDK already makes the jar about 85 MB.

## Git

- The default branch is `main`.
- Commit subjects use a type prefix and a short summary: `feat:`, `fix:`, `docs:`, `test:`, `chore:`.
- Commit or push only when the user asks.

## Not done yet

Do not assume any of these exist:

- A Dockerfile or any deployment. Vercel, where the Python API runs, has no Java runtime.
- A run against a real Firebase project, or a check that the Firestore index for the session list exists.
- The Socket.IO live push of the Flask backend. This API is REST only.
- A browser test of the SwingLab frontend against this API.

## Keeping this file current

Update this file when a package is added or removed, the way the contract is tested changes, the reference page changes how it is checked, a Java or Spring Boot version changes, or an item under "Not done yet" gets done.
