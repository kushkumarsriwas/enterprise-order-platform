package com.enterprise.auth.keys;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Keys")
public class JwksController {

    private final SigningKeyService signingKeyService;

    public JwksController(SigningKeyService signingKeyService) {
        this.signingKeyService = signingKeyService;
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Public keys used to verify access tokens (JWKS)")
    public ResponseEntity<String> jwks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(signingKeyService.jwksJson());
    }
}
