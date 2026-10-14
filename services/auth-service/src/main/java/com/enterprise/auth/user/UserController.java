package com.enterprise.auth.user;

import com.enterprise.auth.common.ApiException;
import com.enterprise.auth.common.PageResponse;
import com.enterprise.auth.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
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
    private final MerchantProfileRepository merchantRepository;

    public UserController(UserRepository userRepository, MerchantProfileRepository merchantRepository) {
        this.userRepository = userRepository;
        this.merchantRepository = merchantRepository;
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    @Operation(summary = "Profile of the logged-in user (with store details for merchants)")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        User user = userRepository.findById(principal.id()).orElseThrow(() -> ApiException.notFound("User not found"));
        return UserResponse.from(user, merchantRepository.findByUserId(user.getId()).orElse(null));
    }

    @PutMapping("/me")
    @Transactional
    @Operation(summary = "Update my contact details (and store details for merchants)")
    public UserResponse updateMe(@Valid @RequestBody ProfileUpdate request,
                                 @AuthenticationPrincipal AuthenticatedUser principal) {
        User user = userRepository.findById(principal.id()).orElseThrow(() -> ApiException.notFound("User not found"));
        user.updateContact(blankToNull(request.fullName()), blankToNull(request.phone()));
        MerchantProfile profile = merchantRepository.findByUserId(user.getId()).orElse(null);
        if (profile != null) {
            profile.update(blankToNull(request.storeDescription()),
                    request.phone() == null || request.phone().isBlank() ? profile.getPhone() : request.phone().trim(),
                    request.city() == null || request.city().isBlank() ? profile.getCity() : request.city().trim());
        }
        return UserResponse.from(user, profile);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    @Operation(summary = "List users, optionally by role (admin)")
    public PageResponse<UserResponse> list(@RequestParam(required = false) Role role,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> users = role == null ? userRepository.findAll(pageable) : userRepository.findByRole(role, pageable);
        Map<Long, MerchantProfile> profiles = merchantRepository.findAllById(
                        users.getContent().stream().map(User::getId).toList())
                .stream().collect(Collectors.toMap(MerchantProfile::getUserId, Function.identity()));
        return PageResponse.from(users, user -> UserResponse.from(user, profiles.get(user.getId())));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Suspend or re-activate an account (admin)")
    public UserResponse changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChange request,
                                     @AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal.id().equals(id)) {
            throw ApiException.badRequest("You cannot change the status of your own account");
        }
        User user = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
        user.setEnabled(request.enabled());
        return UserResponse.from(user, merchantRepository.findByUserId(id).orElse(null));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ProfileUpdate(@Size(max = 120) String fullName,
                                @Size(max = 30) @Pattern(regexp = "^$|^[+0-9 ()-]{7,30}$", message = "is not a valid phone number") String phone,
                                @Size(max = 500) String storeDescription,
                                @Size(max = 60) String city) {
    }

    public record StatusChange(@NotNull Boolean enabled) {
    }
}
