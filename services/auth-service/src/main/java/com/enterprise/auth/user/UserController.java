package com.enterprise.auth.user;

import com.enterprise.auth.common.ApiException;
import com.enterprise.auth.common.PageResponse;
import com.enterprise.auth.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    @Operation(summary = "Profile of the logged-in user")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return userRepository.findById(principal.id())
                .map(UserResponse::from)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List users (admin)")
    public PageResponse<UserResponse> list(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(userRepository.findAll(pageable), UserResponse::from);
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Change a user's role (admin)")
    public UserResponse changeRole(@PathVariable Long id, @Valid @RequestBody RoleChangeRequest request,
                                   @AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal.id().equals(id)) {
            throw ApiException.badRequest("You cannot change your own role");
        }
        User user = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
        user.setRole(request.role());
        return UserResponse.from(user);
    }

    public record RoleChangeRequest(@NotNull Role role) {
    }
}
