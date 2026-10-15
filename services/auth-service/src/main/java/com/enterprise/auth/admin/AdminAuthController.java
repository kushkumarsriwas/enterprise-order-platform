package com.enterprise.auth.admin;

import com.enterprise.auth.audit.ClientInfo;
import com.enterprise.auth.auth.AuthDtos.AuthResponse;
import com.enterprise.auth.auth.AuthDtos.LoginRequest;
import com.enterprise.auth.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sign-in of the admin console (a separate web application). The marketplace sign-in never accepts the admin. */
@RestController
@RequestMapping("/api/admin/auth")
@Tag(name = "Admin - sign in")
public class AdminAuthController {

    private final AuthService authService;

    public AdminAuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Admin console sign-in (short-lived token)")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.adminLogin(request, ClientInfo.from(http));
    }
}
