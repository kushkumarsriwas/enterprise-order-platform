package com.enterprise.auth.admin;

import com.enterprise.auth.admin.AdminDtos.AccountStats;
import com.enterprise.auth.admin.AdminDtos.AdminUserDetail;
import com.enterprise.auth.admin.AdminDtos.AdminUserResponse;
import com.enterprise.auth.admin.AdminDtos.StatusChange;
import com.enterprise.auth.admin.AdminUserService.UserFilter;
import com.enterprise.auth.common.PageResponse;
import com.enterprise.auth.reporting.Report;
import com.enterprise.auth.reporting.ReportFiles;
import com.enterprise.auth.security.AuthenticatedUser;
import com.enterprise.auth.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Customers and sellers of the platform (admin console). */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - customers and sellers")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Search accounts: role, status, text (name, e-mail, phone, store), joined date, sort")
    public PageResponse<AdminUserResponse> list(@RequestParam(required = false) Role role,
                                                @RequestParam(required = false) Boolean active,
                                                @RequestParam(required = false) String search,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate joinedFrom,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate joinedTo,
                                                @RequestParam(defaultValue = "newest") String sort,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(service.search(new UserFilter(role, active, search, joinedFrom, joinedTo, sort), page,
                size), Function.identity());
    }

    @GetMapping("/stats")
    @Operation(summary = "Account totals, suspensions, sign-ins and daily sign-ups")
    public AccountStats stats() {
        return service.stats();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Everything about one account: profile, store, address, recent sign-ins")
    public AdminUserDetail get(@PathVariable Long id) {
        return service.detail(id);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Suspend or reactivate an account (a suspended store disappears from the marketplace)")
    public AdminUserResponse status(@PathVariable Long id, @Valid @RequestBody StatusChange request,
                                    @AuthenticationPrincipal AuthenticatedUser admin) {
        return service.changeStatus(id, request.active(), request.reason(), admin.username());
    }

    @GetMapping("/export/{format}")
    @Operation(summary = "Download the filtered account list as xlsx or pdf")
    public ResponseEntity<byte[]> export(@PathVariable String format,
                                         @RequestParam(required = false) Role role,
                                         @RequestParam(required = false) Boolean active,
                                         @RequestParam(required = false) String search,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate joinedFrom,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate joinedTo,
                                         @RequestParam(defaultValue = "newest") String sort) {
        List<AdminUserResponse> users = service.export(new UserFilter(role, active, search, joinedFrom, joinedTo, sort));
        String title = role == Role.SELLER ? "Sellers" : role == Role.CUSTOMER ? "Customers" : "Accounts";
        Report report = new Report(title + " - Nexora", users.size() + " account(s)", List.of(
                new Report.Metric("Accounts", users.size()),
                new Report.Metric("Active", users.stream().filter(AdminUserResponse::active).count()),
                new Report.Metric("Suspended", users.stream().filter(u -> !u.active()).count())),
                List.of(new Report.Table(title, List.of("ID", "Username", "Name", "E-mail", "Phone", "Role", "Store",
                        "City", "Status", "Joined", "Last sign-in"),
                        users.stream().map(u -> List.<Object>of(u.id(), u.username(), nz(u.fullName()), u.email(),
                                nz(u.phone()), u.role(), u.store() == null ? "-" : u.store().storeName(),
                                u.store() != null ? u.store().city() : u.address() != null ? nz(u.address().city()) : "-",
                                u.active() ? "Active" : "Suspended", u.createdAt().toString().substring(0, 10),
                                u.lastLoginAt() == null ? "-" : u.lastLoginAt().toString().substring(0, 16).replace('T', ' ')))
                                .toList())));
        return ReportFiles.download(report, format, title.toLowerCase() + "-" + LocalDate.now());
    }

    private static String nz(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
