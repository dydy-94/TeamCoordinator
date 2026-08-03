package org.cmb.teamcoordinator.api;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        return status("UP");
    }

    @GetMapping("/ready")
    public Map<String, Object> ready() {
        return status("READY");
    }

    private Map<String, Object> status(String status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("service", "TeamCoordinator");
        body.put("time", Instant.now().toString());
        return body;
    }
}
