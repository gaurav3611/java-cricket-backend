"""
Records how the original Flask backend (backend/app.py) answers a fixed list
of requests, and writes the result to golden.json next to this script.

ParityTest.java replays the same requests against the Spring Boot API and
compares the answers, so golden.json is the frozen contract for the port.

The Flask app runs in demo mode (no Firebase credentials) inside Flask's test
client, so nothing is sent over the network and no data is stored.

The Flask backend lives in a different repository, https://github.com/gaurav3611/Cricket.
By default it is expected in a folder named Cricket next to this repository. Set
CRICKET_REPO to use a checkout somewhere else. Its Python dependencies must be installed
(pip install -r backend/requirements.txt in that repository).

Run from the root of this repository:

    PYTHONDONTWRITEBYTECODE=1 python src/test/resources/parity/generate_golden.py
"""

import json
import math
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.abspath(os.path.join(HERE, "..", "..", "..", ".."))
REPO = os.path.abspath(os.environ.get("CRICKET_REPO") or os.path.join(PROJECT, "..", "Cricket"))

if not os.path.isfile(os.path.join(REPO, "backend", "app.py")):
    sys.exit("Flask backend not found: %s has no backend/app.py.\n"
             "Clone https://github.com/gaurav3611/Cricket next to this repository, "
             "or set CRICKET_REPO to a checkout of it." % REPO)

# Force demo mode: point the key path at a file that does not exist.
os.environ["FIREBASE_SERVICE_ACCOUNT_PATH"] = os.path.join(HERE, "no-such-key.json")
sys.path.insert(0, os.path.join(REPO, "backend"))

import app as flask_backend  # noqa: E402

JSON_HEADERS = {"Content-Type": "application/json"}

# Keys whose values change on every run. Only their presence and JSON type are compared.
VOLATILE_KEYS = [
    "session_id", "started_at", "ended_at", "duration_seconds",
    "timestamp", "last_seen", "updated_at",
]

steps = []


def step(name, method, path, body=None, headers=None, compare="json"):
    """compare: json = status and full body, error = status and an 'error' key, status = status only."""
    steps.append({
        "name": name, "method": method, "path": path,
        "headers": headers or {}, "body": body, "compare": compare,
    })


def reading(device_id, ax, ay, az, gx, gy, gz):
    values = {"ax": ax, "ay": ay, "az": az, "gx": gx, "gy": gy, "gz": gz}
    payload = {"device_id": device_id}
    payload.update({k: round(v, 3) for k, v in values.items()})
    payload["ts"] = 0
    return json.dumps(payload)


def swing_series(count, peaks, side):
    """A deterministic series of readings: quiet backlift, one swing, follow-through, repeated."""
    out = []
    for i in range(count):
        swing_no = min(i // 80, len(peaks) - 1)
        phase = (i % 80) / 80.0
        if phase < 0.25:
            out.append((0.2 * math.sin(i), 1.0 + 0.1 * math.cos(i), 0.15 * math.sin(i * 0.5),
                        12 * math.sin(i * 0.7), 6 + 3 * math.cos(i * 0.3), 2 * math.sin(i * 1.1)))
        elif phase < 0.5:
            intensity = math.sin((phase - 0.25) / 0.25 * math.pi)
            peak = peaks[swing_no]
            out.append((intensity * 3.2, intensity * 2.4, intensity * 1.6,
                        -intensity * peak * 0.3, intensity * peak,
                        side[swing_no] * intensity * peak * 0.15))
        else:
            decay = max(0.0, 1 - (phase - 0.5) * 3)
            out.append((decay * 0.8, 1 + decay * 0.5, decay * 0.2,
                        -decay * 40, decay * 60, decay * 8 * math.sin(i)))
    return out


# Degrees per second of rotation that the Flask formula turns into 1 km/h of bat speed.
DPS_PER_KMH = 1 / ((math.pi / 180) * 0.55 * 3.6)


def at_speed(kmh, accel=1.0, gz=0.0):
    """A reading whose bat speed is the given km/h and whose acceleration magnitude is `accel` g."""
    return (accel * 0.6, accel * 0.8, 0.0, 0.0, kmh * DPS_PER_KMH, gz)


def gentle_session():
    """Two moderate swings with small sideways rotation, so every score lands mid-range."""
    out = []

    def rest(count):
        for _ in range(count):
            i = len(out)
            out.append((0.05 * math.sin(i), 1.0, 0.05 * math.cos(i), 2.0 * math.sin(i * 0.5), 4.0, 3.0 * math.sin(i * 0.9)))

    def swing(gy_values, hard_hit_at, borderline_at):
        for j, gy in enumerate(gy_values):
            i = len(out)
            accel = 2.8 if j == hard_hit_at else 2.5 if j == borderline_at else 1.4
            out.append((accel * 0.6, accel * 0.8, 0.0, -gy * 0.2, gy, 3.0 * math.sin(i * 0.9)))

    rest(5)
    swing([820, 900, 980, 1040, 1000, 930, 860, 800], hard_hit_at=3, borderline_at=5)
    rest(5)
    swing([840, 910, 960, 990, 950, 900, 850, 810], hard_hit_at=None, borderline_at=2)
    rest(10)
    return out


# ---- 1. Read endpoints in demo mode -------------------------------------------------
step("health at start", "GET", "/api/health")
step("profile (demo)", "GET", "/api/profile")
step("sessions (demo)", "GET", "/api/sessions")
step("session by id (demo)", "GET", "/api/session/abc12345")
step("delete session (demo)", "DELETE", "/api/session/abc12345")
step("update profile (demo)", "PUT", "/api/profile",
     body=json.dumps({"display_name": "Asha", "team": "Blue"}), headers=JSON_HEADERS)
step("update profile, no body (demo)", "PUT", "/api/profile")
step("device status, nothing seen yet", "GET", "/api/esp32/status")

# ---- 2. Sensor endpoint: malformed and unusual input --------------------------------
step("sensor: empty object", "POST", "/api/sensor-data", body="{}", headers=JSON_HEADERS)
step("sensor: empty array", "POST", "/api/sensor-data", body="[]", headers=JSON_HEADERS)
step("sensor: non-empty array", "POST", "/api/sensor-data", body="[1]", headers=JSON_HEADERS, compare="error")
step("sensor: invalid JSON", "POST", "/api/sensor-data", body="not json", headers=JSON_HEADERS, compare="error")
step("sensor: no body", "POST", "/api/sensor-data", compare="error")
step("sensor: non-numeric value", "POST", "/api/sensor-data",
     body='{"device_id":"BAT_T","ax":"abc"}', headers=JSON_HEADERS, compare="error")
step("sensor: null value", "POST", "/api/sensor-data",
     body='{"device_id":"BAT_T","ax":null}', headers=JSON_HEADERS, compare="error")
step("sensor: valid reading sent as text/plain", "POST", "/api/sensor-data",
     body=reading("BAT_T", 0.1, 1.0, 0.1, 5, 5, 0.5), headers={"Content-Type": "text/plain"})
step("sensor: valid reading with no content type", "POST", "/api/sensor-data",
     body=reading("BAT_T", 0.1, 1.0, 0.1, 5, 5, 0.5))
step("sensor: numbers sent as strings", "POST", "/api/sensor-data",
     body='{"device_id":"BAT_S","ax":"1.5","ay":"0.5","az":"0.25","gx":"100","gy":"200","gz":"-3"}',
     headers=JSON_HEADERS)
step("sensor: only a device id", "POST", "/api/sensor-data", body='{"device_id":"BAT_X"}', headers=JSON_HEADERS)
step("sensor: no device id", "POST", "/api/sensor-data", body='{"ax":0.5,"gy":40}', headers=JSON_HEADERS)
step("device status, four devices seen", "GET", "/api/esp32/status")

# ---- 3. Session lifecycle ----------------------------------------------------------
step("stop with no active session", "POST", "/api/session/stop")
step("start session A", "POST", "/api/session/start", body='{"device_id":"BAT_T"}', headers=JSON_HEADERS)
step("health with one active session", "GET", "/api/health")
step("stop session A with no readings", "POST", "/api/session/stop")

step("start session B on the default device", "POST", "/api/session/start", body="{}", headers=JSON_HEADERS)
for i, r in enumerate(swing_series(12, [900], [0])):
    step("session B: reading %d for a different device" % i, "POST", "/api/sensor-data",
         body=reading("BAT_T", *r), headers=JSON_HEADERS)
step("stop session B, readings were for another device", "POST", "/api/session/stop")

step("start session C: three swings", "POST", "/api/session/start", body='{"device_id":"BAT_T"}', headers=JSON_HEADERS)
for i, r in enumerate(swing_series(240, [900, 1600, 5200], [-1.5, 0, 1.5])):
    step("session C: reading %d" % i, "POST", "/api/sensor-data", body=reading("BAT_T", *r), headers=JSON_HEADERS)
    if i % 60 == 30:
        step("session C: unrelated device after reading %d" % i, "POST", "/api/sensor-data",
             body=reading("BAT_OTHER", 4.0, 4.0, 4.0, 3000, 3000, 3000), headers=JSON_HEADERS)
step("stop session C", "POST", "/api/session/stop")

step("start session D: five readings", "POST", "/api/session/start", body='{"device_id":"BAT_T"}', headers=JSON_HEADERS)
for i, r in enumerate(swing_series(40, [1200], [1.5])[18:23]):
    step("session D: reading %d" % i, "POST", "/api/sensor-data", body=reading("BAT_T", *r), headers=JSON_HEADERS)
step("stop session D", "POST", "/api/session/stop")

step("start session E: one reading", "POST", "/api/session/start", body='{"device_id":"BAT_T"}', headers=JSON_HEADERS)
step("session E: reading 0", "POST", "/api/sensor-data",
     body=reading("BAT_T", 2.0, 2.0, 1.5, -300, 1400, 80), headers=JSON_HEADERS)
step("stop session E", "POST", "/api/session/stop")

step("start session F: bat at rest", "POST", "/api/session/start", body='{"device_id":"BAT_T"}', headers=JSON_HEADERS)
for i in range(15):
    step("session F: reading %d" % i, "POST", "/api/sensor-data",
         body=reading("BAT_T", 0.0, 1.0, 0.0, 1.0, 2.0, 0.5), headers=JSON_HEADERS)
step("stop session F", "POST", "/api/session/stop")

step("start session G: restarted before stopping", "POST", "/api/session/start", body='{"device_id":"BAT_T"}', headers=JSON_HEADERS)
step("session G: reading before restart", "POST", "/api/sensor-data",
     body=reading("BAT_T", 3.0, 3.0, 3.0, 900, 900, 900), headers=JSON_HEADERS)
step("start session G again, replaces the first", "POST", "/api/session/start", body='{"device_id":"BAT_T"}', headers=JSON_HEADERS)
step("stop session G, the reading belonged to the replaced session", "POST", "/api/session/stop")

def record_session(label, title, readings):
    step("start session %s: %s" % (label, title), "POST", "/api/session/start",
         body='{"device_id":"BAT_T"}', headers=JSON_HEADERS)
    for i, r in enumerate(readings):
        step("session %s: reading %d" % (label, i), "POST", "/api/sensor-data",
             body=reading("BAT_T", *r), headers=JSON_HEADERS)
    step("stop session %s" % label, "POST", "/api/session/stop")


# Sessions H to N put every score in its middle range and sit on each threshold in the
# scoring code, so a changed constant or comparison shows up as a different answer.
record_session("H", "two gentle swings, mid-range scores", gentle_session())

# Follow-through looks at up to 10 readings from the first peak and needs more than 3 of them.
record_session("I", "peak 4 readings before the end",
               [at_speed(v) for v in [5, 8, 12, 18, 22, 26, 30, 33, 41, 30, 21, 12]])
record_session("J", "peak 3 readings before the end",
               [at_speed(v) for v in [5, 8, 12, 18, 22, 26, 30, 33, 36, 41, 30, 21]])
# Follow-through is only computed for more than 10 readings.
record_session("K", "exactly 10 readings", [at_speed(v) for v in [5, 12, 22, 30, 41, 33, 26, 18, 12, 8]])
record_session("L", "exactly 11 readings", [at_speed(v) for v in [5, 12, 22, 30, 41, 33, 26, 18, 12, 8, 5]])

# A swing is counted when speed goes above 25 km/h straight after a reading below 15 km/h.
record_session("M", "swing detection thresholds",
               [at_speed(v) for v in [10, 26,        # counted
                                      16, 30,        # not counted: previous reading was not below 15
                                      10, 24.9,      # not counted: did not go above 25
                                      14.9, 25.1,    # counted
                                      15.1, 40,      # not counted
                                      0, 0, 60]])    # counted

# Timing uses readings above 20 km/h. A sweet spot hit needs more than 2.5 g and more than 20 km/h.
record_session("N", "timing and sweet spot thresholds",
               [at_speed(10), at_speed(26, accel=2.5), at_speed(20.04, accel=3.0), at_speed(20.06, accel=2.51),
                at_speed(19.9, accel=4.0), at_speed(30, accel=2.6), at_speed(23, accel=2.4), at_speed(27, accel=1.0, gz=2.0),
                at_speed(24, gz=-2.0), at_speed(22, gz=1.0), at_speed(9.5 / DPS_PER_KMH, gz=0.5), at_speed(10.5 / DPS_PER_KMH)])

# The average speed here is exactly 20.25. Python rounds that tie to the even digit, 20.2,
# where ordinary half-up rounding would give 20.3.
record_session("O", "average on a rounding tie", [at_speed(20.0), at_speed(20.5)])

step("health at end", "GET", "/api/health")
step("device status at end", "GET", "/api/esp32/status")

# ---- 4. Routing, CORS and static files ---------------------------------------------
step("unknown api route", "GET", "/api/nope", compare="status")
step("wrong method", "POST", "/api/health", compare="status")
step("cors: simple request", "GET", "/api/health", headers={"Origin": "https://example.org"})
step("cors: preflight", "OPTIONS", "/api/profile", compare="status",
     headers={"Origin": "https://example.org", "Access-Control-Request-Method": "PUT",
              "Access-Control-Request-Headers": "authorization,content-type"})
# The API answers JSON whatever the caller says it prefers. A browser's address bar sends the
# second Accept header below.
step("accept: xml only", "GET", "/api/health", headers={"Accept": "application/xml"})
step("accept: browser navigation", "GET", "/api/profile",
     headers={"Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"})
step("accept: xml only, error answer", "POST", "/api/session/stop", headers={"Accept": "application/xml"})
step("static: index", "GET", "/", compare="status")
step("static: dashboard", "GET", "/dashboard.html", compare="status")
step("static: script", "GET", "/js/three-bat.js", compare="status")
step("static: missing file", "GET", "/no-such-file.html", compare="status")

# ---- 5. Demo simulation, last because it never stops --------------------------------
step("start demo simulation", "POST", "/api/demo/start")


def main():
    client = flask_backend.app.test_client()
    assert flask_backend.demo_mode, "expected the Flask app to be in demo mode"

    for s in steps:
        kwargs = {"headers": s["headers"]}
        if s["body"] is not None:
            kwargs["data"] = s["body"]
        resp = client.open(s["path"], method=s["method"], **kwargs)
        s["status"] = resp.status_code
        s["content_type"] = resp.headers.get("Content-Type", "").split(";")[0]
        s["allow_origin"] = resp.headers.get("Access-Control-Allow-Origin")
        s["json"] = resp.get_json(silent=True) if s["compare"] == "json" else None

    try:
        commit = subprocess.check_output(["git", "-C", REPO, "rev-parse", "--short", "HEAD"], text=True).strip()
    except Exception:
        commit = "unknown"

    golden = {
        "source": "backend/app.py in demo mode, replayed through the Flask test client",
        "source_commit": commit,
        "volatile_keys": VOLATILE_KEYS,
        "steps": steps,
    }
    out_path = os.path.join(HERE, "golden.json")
    with open(out_path, "w") as f:
        json.dump(golden, f, indent=1)
        f.write("\n")
    print("wrote %d steps to %s" % (len(steps), out_path))


if __name__ == "__main__":
    main()
