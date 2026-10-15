package com.enterprise.auth.admin;

import com.enterprise.auth.audit.LoginEvent;
import com.enterprise.auth.user.UserResponse.AddressResponse;
import com.enterprise.auth.user.UserResponse.StoreResponse;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class AdminDtos {

    private AdminDtos() {
    }

    /** One customer or seller as the administrator sees it (everything, including status history). */
    public record AdminUserResponse(Long id, String username, String email, String fullName, String phone, String role,
                                    boolean active, String suspensionReason, Instant statusChangedAt,
                                    Instant createdAt, Instant lastLoginAt, AddressResponse address,
                                    StoreResponse store) {
    }

    public record AdminUserDetail(AdminUserResponse user, List<LoginEventResponse> recentLogins,
                                  long failedLoginsLast30Days) {
    }

    public record StatusChange(@NotNull Boolean active, @Size(max = 255) String reason) {
    }

    public record LoginEventResponse(Long id, Long userId, String username, String portal, boolean success,
                                     String failureReason, String ipAddress, String userAgent, Instant createdAt) {

        public static LoginEventResponse from(LoginEvent event) {
            return new LoginEventResponse(event.getId(), event.getUserId(), event.getUsername(), event.getPortal().name(),
                    event.isSuccess(), event.getFailureReason(), event.getIpAddress(), event.getUserAgent(),
                    event.getCreatedAt());
        }
    }

    public record DailySignups(String date, long customers, long sellers) {
    }

    public record AccountStats(long customers, long sellers, long suspendedCustomers, long suspendedSellers,
                               long signInsLast24h, long failedSignInsLast24h, long failingIpsLast24h,
                               List<DailySignups> signupsLast30Days) {
    }
}
