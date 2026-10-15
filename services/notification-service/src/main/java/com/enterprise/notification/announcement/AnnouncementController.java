package com.enterprise.notification.announcement;

import com.enterprise.notification.common.ApiException;
import com.enterprise.notification.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Platform announcements: shown as a banner to visitors, customers and/or sellers; managed by the admin. */
@RestController
@Tag(name = "Announcements")
public class AnnouncementController {

    private final AnnouncementRepository repository;

    public AnnouncementController(AnnouncementRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/announcements")
    @Transactional(readOnly = true)
    @Operation(summary = "Live announcements for the caller (visitors see EVERYONE only; public)")
    public List<AnnouncementResponse> live(@AuthenticationPrincipal AuthenticatedUser user) {
        List<AnnouncementAudience> audiences = new ArrayList<>(List.of(AnnouncementAudience.EVERYONE));
        if (user != null && user.isCustomer()) {
            audiences.add(AnnouncementAudience.CUSTOMERS);
        }
        if (user != null && user.isSeller()) {
            audiences.add(AnnouncementAudience.SELLERS);
        }
        return repository.findLive(audiences, Instant.now()).stream().map(AnnouncementResponse::from).toList();
    }

    @GetMapping("/api/admin/announcements")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    @Operation(summary = "All announcements (admin)")
    public List<AnnouncementResponse> all() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(AnnouncementResponse::from).toList();
    }

    @PostMapping("/api/admin/announcements")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @Operation(summary = "Publish an announcement (admin)")
    public AnnouncementResponse create(@Valid @RequestBody AnnouncementRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser admin) {
        validate(request);
        return AnnouncementResponse.from(repository.save(new Announcement(request.title().trim(),
                request.message().trim(), request.audience(), request.level(), request.startsAt(), request.endsAt(),
                admin.username())));
    }

    @PutMapping("/api/admin/announcements/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Edit, schedule or switch off an announcement (admin)")
    public AnnouncementResponse update(@PathVariable Long id, @Valid @RequestBody AnnouncementRequest request) {
        validate(request);
        Announcement announcement = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Announcement " + id + " not found"));
        announcement.update(request.title().trim(), request.message().trim(), request.audience(), request.level(),
                request.startsAt(), request.endsAt(), request.active() == null || request.active());
        return AnnouncementResponse.from(announcement);
    }

    @DeleteMapping("/api/admin/announcements/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Delete an announcement (admin)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private static void validate(AnnouncementRequest request) {
        if (request.startsAt() != null && request.endsAt() != null && !request.endsAt().isAfter(request.startsAt())) {
            throw ApiException.badRequest("The end must be after the start");
        }
    }

    public record AnnouncementRequest(@NotBlank @Size(max = 120) String title, @NotBlank @Size(max = 500) String message,
                                      @NotNull AnnouncementAudience audience,
                                      @NotBlank @Pattern(regexp = "INFO|SUCCESS|WARNING") String level,
                                      Instant startsAt, Instant endsAt, Boolean active) {
    }

    public record AnnouncementResponse(Long id, String title, String message, String audience, String level,
                                       Instant startsAt, Instant endsAt, boolean active, String createdBy,
                                       Instant createdAt) {

        static AnnouncementResponse from(Announcement a) {
            return new AnnouncementResponse(a.getId(), a.getTitle(), a.getMessage(), a.getAudience().name(),
                    a.getLevel(), a.getStartsAt(), a.getEndsAt(), a.isActive(), a.getCreatedBy(), a.getCreatedAt());
        }
    }
}
