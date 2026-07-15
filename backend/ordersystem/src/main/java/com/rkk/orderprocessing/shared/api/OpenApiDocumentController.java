package com.rkk.orderprocessing.shared.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Serves the checked-in OpenAPI contract only when the local Swagger UI is enabled. */
@RestController
@ConditionalOnProperty(name = "springdoc.swagger-ui.enabled", havingValue = "true", matchIfMissing = true)
public class OpenApiDocumentController {

    private static final MediaType YAML = MediaType.parseMediaType("application/yaml");

    /** Returns the canonical machine-readable contract consumed by Swagger UI. */
    @GetMapping(value = "/openapi.yaml", produces = "application/yaml")
    public ResponseEntity<Resource> openApiDocument() {
        return ResponseEntity.ok()
                .contentType(YAML)
                .body(new ClassPathResource("openapi/openapi.yaml"));
    }
}
