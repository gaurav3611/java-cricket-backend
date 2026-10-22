package com.sswingg.api.sensor;

import java.util.Collection;
import java.util.Map;

import com.sswingg.api.error.GlobalExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api")
public class SensorController {

    private static final Logger log = LoggerFactory.getLogger(SensorController.class);

    private final SensorService sensorService;
    private final JsonMapper jsonMapper;

    public SensorController(SensorService sensorService, JsonMapper jsonMapper) {
        this.sensorService = sensorService;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Receives one reading from the bat: {device_id, ax, ay, az, gx, gy, gz, ts}.
     * No authentication, as in the Flask app.
     *
     * The body is read as text and parsed here, whatever the Content-Type header says,
     * because the Flask route did the same (get_json(force=True)) and the bat firmware
     * is not in this repository to check which header it sends.
     *
     * Status codes follow the Flask route: 400 for an empty JSON value, 500 for anything
     * that cannot be parsed or is not numeric.
     */
    @PostMapping("/sensor-data")
    public ResponseEntity<Map<String, Object>> receive(@RequestBody(required = false) String body) {
        try {
            Object parsed = jsonMapper.readValue(body == null ? "" : body, Object.class);
            if (isEmpty(parsed)) {
                return ResponseEntity.badRequest().body(Map.of("error", "No data received"));
            }
            if (!(parsed instanceof Map<?, ?> data)) {
                throw new IllegalArgumentException("Expected a JSON object");
            }

            Object deviceId = data.get("device_id");
            RawReading raw = new RawReading(
                    number(data, "ax"), number(data, "ay"), number(data, "az"),
                    number(data, "gx"), number(data, "gy"), number(data, "gz"));
            sensorService.ingest(deviceId == null ? "unknown" : String.valueOf(deviceId), raw);

            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (Exception e) {
            log.warn("[ERR] Sensor data error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", GlobalExceptionHandler.describe(e)));
        }
    }

    /** Which devices have sent data, and how recently. No authentication, as in the Flask app. */
    @GetMapping("/esp32/status")
    public DeviceStatusResponse status() {
        return sensorService.deviceStatuses();
    }

    /** True for the JSON values Python treats as false: null, {}, [], "", 0 and false. */
    private static boolean isEmpty(Object value) {
        return value == null
                || (value instanceof Map<?, ?> map && map.isEmpty())
                || (value instanceof Collection<?> collection && collection.isEmpty())
                || (value instanceof String text && text.isEmpty())
                || (value instanceof Number number && number.doubleValue() == 0)
                || Boolean.FALSE.equals(value);
    }

    /** A missing field is 0. Numbers and numeric strings are accepted, as Python's float() accepts them. */
    private static double number(Map<?, ?> data, String key) {
        if (!data.containsKey(key)) {
            return 0.0;
        }
        Object value = data.get(key);
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            return Double.parseDouble(s.trim());
        }
        if (value instanceof Boolean b) {
            return b ? 1.0 : 0.0;
        }
        throw new IllegalArgumentException("Field '" + key + "' must be a number");
    }
}
