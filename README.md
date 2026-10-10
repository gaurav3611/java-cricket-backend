# Java Cricket Backend

The REST API for SwingLab, a cricket bat swing analytics app, written in Java with Spring Boot.

It is a rewrite of the Flask backend in the [Cricket](https://github.com/gaurav3611/Cricket) repository (`backend/app.py` at commit `bc4ea4b`). It has the same paths, methods, JSON field names and status codes, and its answers are tested against a recording of the Flask backend's answers.

**Status:** it builds, passes its tests and runs locally. It is not deployed. It has not been run against a real Firebase project yet, see [Not checked yet](#not-checked-yet).

| | |
|---|---|
| Framework | Spring Boot 4.1.1, Spring MVC, embedded Tomcat |
| Language | Java 21 |
| Build | Maven, through the wrapper script `./mvnw` |
| Firebase | Firebase Admin SDK for Java 9.11.0: ID tokens, user lookup, Firestore |

## Run it

You need a Java 21 JDK. Nothing else has to be installed. The `./mvnw` script downloads Maven and the libraries on first use.

```bash
./mvnw spring-boot:run
```

Then check it:

```bash
curl http://localhost:5050/api/health
```

Other commands:

```bash
./mvnw test                                           # run the tests
./mvnw package                                        # build target/swinglab-api-0.0.1-SNAPSHOT.jar
java -jar target/swinglab-api-0.0.1-SNAPSHOT.jar      # run the built jar
PORT=5051 ./mvnw spring-boot:run                      # use another port
```

Things to know:

- **Java 21 must be the active JDK.** With an older one the build stops with `release version 21 not supported`, and the jar stops with `UnsupportedClassVersionError`. If your default `java` is older, point `JAVA_HOME` at a Java 21 installation first. With Homebrew's `openjdk@21` on an Apple Silicon Mac that is:

  ```bash
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
  ```

- **With no Firebase key it starts in demo mode.** It logs a warning, skips sign-in checks, treats every caller as the user `demo-user` and stores nothing. This is the normal state for local work.
- **The frontend is optional.** If the Cricket repository is cloned in a folder named `Cricket` next to this one, its `frontend/` folder is served at `/`, so `http://localhost:5050` shows the app. Otherwise only `/api/...` answers. Set `FRONTEND_DIR` to serve a frontend from somewhere else.
- **Start it from the repository root.** The `.env` file and a relative `FRONTEND_DIR` are resolved from the directory you start in.

## Configuration

Set these as environment variables, or as `KEY=VALUE` lines in a `.env` file in the repository root. Copy [.env.example](.env.example) to start. Git ignores `.env` and `serviceAccountKey.json`. Never commit either.

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `5050` | Port to listen on. |
| `FIREBASE_SERVICE_ACCOUNT_PATH` | `serviceAccountKey.json` | Path to the Firebase service account key file. |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | empty | The whole key as text. Wins over the path. Meant for a host's secret settings. |
| `FRONTEND_DIR` | `../Cricket/frontend` | Folder served as a static site at `/`. |

With neither key variable pointing at a key, the API runs in demo mode. A key that is present but unreadable stops the application from starting, so a broken key can never silently switch sign-in checks off.

## Endpoints

| Method and path | Sign-in | Purpose |
|---|---|---|
| `POST /api/sensor-data` | no | One reading from the bat: `device_id, ax, ay, az, gx, gy, gz, ts` |
| `GET /api/esp32/status` | no | Devices that have sent readings, and how recently |
| `POST /api/session/start` | yes | Start recording. Optional body `{"device_id": "..."}` |
| `POST /api/session/stop` | yes | Stop recording, compute the scores, store them |
| `GET /api/sessions` | yes | The caller's 50 newest stored sessions |
| `GET /api/session/{id}` | yes | One stored session, owner only |
| `DELETE /api/session/{id}` | yes | Delete a stored session, owner only |
| `GET /api/profile` | yes | Account details plus stats added up from stored sessions |
| `PUT /api/profile` | yes | Update `display_name`, `team`, `batting_style` |
| `POST /api/demo/start` | no | Start simulated readings for the device `DEMO_BAT` |
| `GET /api/health` | no | Status and counters |

Sign-in means the header `Authorization: Bearer <Firebase ID token>`. A missing or invalid token gets `401`. In demo mode no token is needed.

Every answer is JSON, including errors, which look like `{"error": "..."}`. Any origin may call `/api/...` from a browser.

A quick tour in demo mode:

```bash
curl -X POST localhost:5050/api/demo/start
curl -X POST localhost:5050/api/session/start -H 'Content-Type: application/json' -d '{"device_id":"DEMO_BAT"}'
curl localhost:5050/api/esp32/status
curl -X POST localhost:5050/api/session/stop
```

Firestore layout: the collection `sessions` holds one document per stopped session, keyed by the session id, with the owner's Firebase uid in the field `uid`. The collection `users` holds profile fields, keyed by uid.

## Tests and the Flask contract

The Flask backend had no tests, so its behavior was recorded and the recording is the contract.

- **The recording.** [golden.json](src/test/resources/parity/golden.json) holds 454 requests and the answers the Flask backend gave in demo mode. They cover every endpoint, malformed input, fifteen recording sessions with several built to sit exactly on each scoring threshold, CORS, `Accept` headers and static files.
- **`ParityTest`** replays those requests against this API over real HTTP and compares status codes and JSON bodies. Numbers must agree to within 0.000000001. Session ids and timestamps are compared by type only.
- **`SecuredModeTest`** covers what the recording cannot, because Flask in demo mode skips it: token checks on every protected route, owner-only read and delete, what is read from and written to storage, and storage failures. Firebase is replaced by stand-ins.
- **`FirebaseWiringTest` and `FirebaseContextTest`** start the real Firebase SDK with a key generated on the spot. They show that a key switches demo mode off, that the real verifier rejects bad tokens, and that a broken key stops startup.
- **`SensorMathTest` and `PyMathTest`** pin the formulas and the rounding to values computed in Python.

The tests need no Firebase project and no other repository.

To check that the comparison can fail, 25 single-line changes were made to the code one at a time: a constant, a threshold, a rounding rule, a removed ownership check, a bypassed token check. The tests failed for every one.

Do not edit `golden.json` by hand. To record again after the Flask backend changes, clone the Cricket repository next to this one, install its `backend/requirements.txt`, and run:

```bash
PYTHONDONTWRITEBYTECODE=1 python src/test/resources/parity/generate_golden.py
```

Set `CRICKET_REPO` if the Cricket checkout is somewhere else. Then run `./mvnw test` to see what this API has to match.

## Differences from the Flask backend

| Area | Flask `backend/app.py` | This API |
|---|---|---|
| Live push | Socket.IO events `sensor_data`, `swing_detected`, `session_started`, `session_stopped` | Not ported. This API is REST only. `connected_clients` in the health answer is always 0. |
| Errors for unknown paths and wrong methods | HTML pages | JSON `{"error": "..."}` with the same status codes |
| Text of `500` errors | Python exception text | Java exception text |
| Start session or update profile with no body | `415` or `400` | Accepted, defaults are used |
| `POST /api/demo/start` called twice | A second simulation runs beside the first | Still one simulation |
| Key present but unreadable | Crash at startup, or in the Vercel version a silent fall back to demo mode | Refuses to start |
| Default port with no config | 5000 | 5050 |
| CORS header on a request with no `Origin` | `Access-Control-Allow-Origin: *` | No CORS header. Browsers always send `Origin` on cross-site calls, so they see no change. |
| Static frontend | Always the `frontend/` folder beside the backend | Optional, from `FRONTEND_DIR` |

Kept on purpose, quirks included:

- `POST /api/sensor-data` reads the body as JSON whatever the `Content-Type` says, and answers `500` for input it cannot parse. The bat firmware is not available to check which header it sends.
- The sweet spot percentage is not capped at 100. One recorded session scores 3700.
- Rounding follows Python, which rounds exact ties to the even digit: 45.25 becomes 45.2. See `PyMath`.
- Timestamps use Python's format, for example `2026-10-10T05:01:09.884261+00:00`. Stored sessions are sorted by this text.
- `POST /api/sensor-data`, `GET /api/esp32/status` and `POST /api/demo/start` need no sign-in.
- The server-side demo simulation produces speeds near zero, because its rotation values sit at the edge of the noise filter.

The Cricket repository also has a smaller Vercel function, `api/index.py`, that had drifted from `backend/app.py`. This API follows `backend/app.py`. Three differences matter if it replaces the Vercel function: sessions are owned through the field `uid` and not `user_id`, profile stats are added up from stored sessions and not read from the user document, and the token `demo-token` is rejected once a key is configured.

## Not checked yet

- **A real Firebase project.** Accepting a genuine ID token, and every Firestore read and write, have only run against stand-ins and a generated key.
- **The Firestore index.** `GET /api/sessions` filters on `uid` and sorts on `started_at`, which needs a composite index in Firestore. Without it the call returns an empty list with an `error` field.
- **A browser session against this API.** The three calls the current frontend makes are covered by the tests, but a signed-in browser run was not done.
- **The bat hardware.** Readings were sent with `curl` and by the tests, not by an ESP32.
- **Docker and deployment.** There is no Dockerfile yet.

## Running it in production

- **Hosting.** It needs a host that runs Java or a Docker image. Vercel, where the Python API runs today, has no Java runtime. Render needs a Docker image for Java.
- **One instance only.** Sessions in progress and the device list are kept in memory. They are lost on restart and are not shared between instances.
- **Memory.** In demo mode on an Apple Silicon Mac the process used about 300 MB, or 230 MB with `-Xmx256m`, and started in under a second. With Firebase connected it will use more.
- **The key.** Give it as the secret `FIREBASE_SERVICE_ACCOUNT_JSON`.

## Code layout

```
src/main/java/com/swinglab/api/
├── auth/       @RequireAuth, the interceptor that checks tokens, TokenVerifier
├── config/     CORS, JSON-only answers, interceptor registration
├── demo/       POST /api/demo/start and the simulator
├── error/      ApiException and the handler that shapes every error as {"error": ...}
├── firebase/   everything that touches the Firebase SDK, and demo mode detection
├── health/     GET /api/health
├── profile/    GET and PUT /api/profile
├── sensor/     POST /api/sensor-data, GET /api/esp32/status, the metric formulas
├── session/    recording, the end-of-session scores, stored history
└── util/       Python-compatible rounding and timestamps
src/main/resources/application.properties
src/test/java/...                    the tests named above
src/test/resources/parity/           generate_golden.py and golden.json
src/test/resources/frontend-fixture/ three small files that stand in for a frontend in ParityTest
```

The formulas live in two files: `sensor/SensorMath.java` for per-reading metrics and `session/SessionAnalytics.java` for the session scores. Both copy the Python arithmetic step for step. A change to either is a change to the contract and needs a new recording.

Only the `firebase/` package imports Firebase classes. The rest of the code reaches Firebase through four small interfaces: `TokenVerifier`, `UserDirectory`, `SessionRepository` and `UserProfileRepository`. That is what lets the tests replace it.
