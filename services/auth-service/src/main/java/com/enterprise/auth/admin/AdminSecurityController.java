package com.enterprise.auth.admin;

import com.enterprise.auth.admin.AdminDtos.LoginEventResponse;
import com.enterprise.auth.audit.LoginEventRepository;
import com.enterprise.auth.common.PageResponse;
import com.enterprise.auth.reporting.Report;
import com.enterprise.auth.reporting.ReportFiles;
import com.enterprise.auth.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Security audit trail: every sign-in attempt on the marketplace and the admin console. */
@RestController
@RequestMapping("/api/admin/security")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - security log")
public class AdminSecurityController {

    private static final Sort NEWEST = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final LoginEventRepository repository;

    public AdminSecurityController(LoginEventRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/logins")
    @Transactional(readOnly = true)
    @Operation(summary = "Sign-in attempts: success / failure, page used, username or IP, date range")
    public PageResponse<LoginEventResponse> logins(@RequestParam(required = false) Boolean success,
                                                   @RequestParam(required = false) Role portal,
                                                   @RequestParam(required = false) String search,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "25") int size) {
        return PageResponse.from(repository.findAll(AdminUserService.loginSpecification(success, portal, search, from, to),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), NEWEST)), LoginEventResponse::from);
    }

    @GetMapping("/logins/export/{format}")
    @Transactional(readOnly = true)
    @Operation(summary = "Download the filtered security log as xlsx or pdf")
    public ResponseEntity<byte[]> export(@PathVariable String format,
                                         @RequestParam(required = false) Boolean success,
                                         @RequestParam(required = false) Role portal,
                                         @RequestParam(required = false) String search,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<LoginEventResponse> events = repository.findAll(
                        AdminUserService.loginSpecification(success, portal, search, from, to), PageRequest.of(0, 5000, NEWEST))
                .map(LoginEventResponse::from).getContent();
        Report report = new Report("Security log - sign-in attempts", events.size() + " event(s)", List.of(
                new Report.Metric("Attempts", events.size()),
                new Report.Metric("Failed", events.stream().filter(e -> !e.success()).count())),
                List.of(new Report.Table("Sign-in attempts", List.of("Time (UTC)", "Username", "Page", "Result", "Reason",
                        "IP address"),
                        events.stream().map(e -> List.<Object>of(e.createdAt().toString().substring(0, 19).replace('T', ' '),
                                e.username(), e.portal(), e.success() ? "Success" : "Failed",
                                e.failureReason() == null ? "-" : e.failureReason(),
                                e.ipAddress() == null ? "-" : e.ipAddress())).toList())));
        return ReportFiles.download(report, format, "security-log-" + LocalDate.now());
    }
}
