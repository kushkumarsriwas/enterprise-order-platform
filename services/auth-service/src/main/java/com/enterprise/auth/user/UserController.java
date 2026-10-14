package com.enterprise.auth.user;

import com.enterprise.auth.security.AuthenticatedUser;
import com.enterprise.auth.user.UserDtos.AddressRequest;
import com.enterprise.auth.user.UserDtos.PasswordChange;
import com.enterprise.auth.user.UserDtos.ProfileUpdate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Your account": every endpoint works on the logged-in user only (the id comes from the JWT). */
@RestController
@RequestMapping("/api/users/me")
@Tag(name = "Your account")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "My profile (with store details for sellers and the saved delivery address)")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return userService.profile(principal.id());
    }

    @PutMapping
    @Operation(summary = "Update my contact details (and store details for sellers)")
    public UserResponse update(@Valid @RequestBody ProfileUpdate request,
                               @AuthenticationPrincipal AuthenticatedUser principal) {
        return userService.updateProfile(principal.id(), request);
    }

    @PutMapping("/address")
    @Operation(summary = "Save my default delivery address (pre-fills the checkout)")
    public UserResponse address(@Valid @RequestBody AddressRequest request,
                                @AuthenticationPrincipal AuthenticatedUser principal) {
        return userService.updateAddress(principal.id(), request);
    }

    @PutMapping("/password")
    @Operation(summary = "Change my password (the current password is required)")
    public ResponseEntity<Void> password(@Valid @RequestBody PasswordChange request,
                                         @AuthenticationPrincipal AuthenticatedUser principal) {
        userService.changePassword(principal.id(), request);
        return ResponseEntity.noContent().build();
    }
}
