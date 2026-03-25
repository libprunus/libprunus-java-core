package org.libprunus.spring.server.management;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ManagementController {

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("health");
    }
}
