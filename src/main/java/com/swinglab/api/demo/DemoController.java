package com.swinglab.api.demo;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final DemoSimulator simulator;

    public DemoController(DemoSimulator simulator) {
        this.simulator = simulator;
    }

    /** No authentication, as in the Flask app. */
    @PostMapping("/start")
    public Map<String, Object> start() {
        simulator.start();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "demo_started");
        response.put("device_id", DemoSimulator.DEVICE_ID);
        return response;
    }
}
