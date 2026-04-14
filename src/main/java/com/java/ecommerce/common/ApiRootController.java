package com.java.ecommerce.common;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class ApiRootController {

    @GetMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> apiRoot() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("service", "ecommerce-api");
        payload.put("status", "ok");
        payload.put("message", "API is running. Use /api/products/cursor and /api/categories.");
        payload.put("timestamp", Instant.now().toString());
        return payload;
    }
}
