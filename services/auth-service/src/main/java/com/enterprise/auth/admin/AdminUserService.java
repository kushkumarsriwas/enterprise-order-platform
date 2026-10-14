package com.enterprise.auth.admin;

import com.enterprise.auth.admin.AdminDtos.AccountStats;
import com.enterprise.auth.admin.AdminDtos.AdminUserDetail;
import com.enterprise.auth.admin.AdminDtos.AdminUserResponse;
import com.enterprise.auth.admin.AdminDtos.DailySignups;
import com.enterprise.auth.admin.AdminDtos.LoginEventResponse;
import com.enterprise.auth.audit.LoginEvent;
import com.enterprise.auth.audit.LoginEventRepository;
import com.enterprise.auth.client.CatalogClient;
import com.enterprise.auth.common.ApiException;
import com.enterprise.auth.common.Like;
import com.enterprise.auth.user.Role;
import com.enterprise.auth.user.SellerProfile;
import com.enterprise.auth.user.SellerProfileRepository;
import com.enterprise.auth.user.User;
import com.enterprise.auth.user.UserRepository;
import com.enterprise.auth.user.UserResponse;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** The administrator's view of every customer and seller account, with search, filters and suspension. */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);
    private static final Map<String, Sort> SORTS = Map.of(
            "newest", Sort.by(Sort.Direction.DESC, "createdAt"),
            "oldest", Sort.by(Sort.Direction.ASC, "createdAt"),
            "name", Sort.by("username"),
            "lastLogin", Sort.by(Sort.Order.desc("lastLoginAt").nullsLast()));

    private final UserRepository userRepository;
    private final SellerProfileRepository sellerRepository;
    private final LoginEventRepository loginEvents;
    private final CatalogClient catalogClient;
    private final TransactionTemplate tx;

    public AdminUserService(UserRepository userRepository, SellerProfileRepository sellerRepository,
                            LoginEventRepository loginEvents, CatalogClient catalogClient, TransactionTemplate tx) {
        this.userRepository = userRepository;
        this.sellerRepository = sellerRepository;
        this.loginEvents = loginEvents;
        this.catalogClient = catalogClient;
        this.tx = tx;
    }

    public record UserFilter(Role role, Boolean active, String search, LocalDate joinedFrom, LocalDate joinedTo,
                             String sort) {
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> search(UserFilter filter, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                SORTS.getOrDefault(filter.sort() == null ? "newest" : filter.sort(), SORTS.get("newest"))
                        .and(Sort.by("id")));
        Page<User> users = userRepository.findAll(specification(filter), pageable);
        return users.map(mapper(users.getContent()));
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> export(UserFilter filter) {
        List<User> users = userRepository.findAll(specification(filter),
                SORTS.getOrDefault(filter.sort() == null ? "newest" : filter.sort(), SORTS.get("newest")));
        List<User> capped = users.size() > 5000 ? users.subList(0, 5000) : users;
        return capped.stream().map(mapper(capped)).toList();
    }

    @Transactional(readOnly = true)
    public AdminUserDetail detail(Long userId) {
        User user = loadAccount(userId);
        List<LoginEventResponse> logins = loginEvents.findByUserIdOrderByCreatedAtDesc(userId, Limit.of(15)).stream()
                .map(LoginEventResponse::from).toList();
        long failed = loginEvents.countByUserIdAndSuccessFalseAndCreatedAtAfter(userId,
                Instant.now().minus(30, ChronoUnit.DAYS));
        return new AdminUserDetail(toResponse(user, sellerRepository.findByUserId(userId).orElse(null)), logins, failed);
    }

    /**
     * Suspends or reactivates an account. For a store, every listing is hidden (or shown) by the inventory
     * service. The remote call happens after the local transaction (no DB connection waits on the network)
     * and is idempotent, so the administrator can simply retry if the inventory service was unreachable.
     */
    public AdminUserResponse changeStatus(Long userId, boolean active, String reason, String adminUsername) {
        AdminUserResponse updated = tx.execute(status -> {
            User user = loadAccount(userId);
            if (active) {
                user.reactivate();
            } else {
                user.suspend(reason == null || reason.isBlank() ? "Suspended by the platform administrator" : reason.trim());
            }
            return toResponse(user, sellerRepository.findByUserId(userId).orElse(null));
        });
        log.info("Admin {} {} {} account {} ({})", adminUsername, active ? "reactivated" : "suspended", updated.role(),
                updated.username(), reason);
        if (Role.SELLER.name().equals(updated.role())) {
            try {
                catalogClient.setStoreSuspended(userId, !active);
            } catch (ApiException ex) {
                throw ApiException.serviceUnavailable("The account was " + (active ? "reactivated" : "suspended")
                        + ", but the store's listings could not be updated yet. Please try again.");
            }
        }
        return updated;
    }

    @Transactional(readOnly = true)
    public AccountStats stats() {
        Instant dayAgo = Instant.now().minus(24, ChronoUnit.HOURS);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Map<String, long[]> perDay = new LinkedHashMap<>();
        for (int i = 29; i >= 0; i--) {
            perDay.put(today.minusDays(i).toString(), new long[2]);
        }
        for (Object[] row : userRepository.signupsPerDay(today.minusDays(29).atStartOfDay().toInstant(ZoneOffset.UTC))) {
            long[] counts = perDay.get(row[0].toString());
            if (counts != null) {
                counts[row[1] == Role.SELLER ? 1 : 0] += ((Number) row[2]).longValue();
            }
        }
        List<DailySignups> signups = new ArrayList<>();
        perDay.forEach((day, counts) -> signups.add(new DailySignups(day, counts[0], counts[1])));
        return new AccountStats(userRepository.countByRole(Role.CUSTOMER), userRepository.countByRole(Role.SELLER),
                userRepository.countByRoleAndEnabledFalse(Role.CUSTOMER),
                userRepository.countByRoleAndEnabledFalse(Role.SELLER),
                loginEvents.countBySuccessTrueAndCreatedAtAfter(dayAgo),
                loginEvents.countBySuccessFalseAndCreatedAtAfter(dayAgo),
                loginEvents.distinctFailingIps(dayAgo), signups);
    }

    // ------------------------------------------------------------------ helpers

    /** Customers and sellers only - the admin account itself is never listed or changed here. */
    private User loadAccount(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> !user.isAdmin())
                .orElseThrow(() -> ApiException.notFound("Account " + userId + " not found"));
    }

    private static Specification<User> specification(UserFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.notEqual(root.get("role"), Role.ADMIN));
            if (filter.role() != null) {
                where.add(cb.equal(root.get("role"), filter.role()));
            }
            if (filter.active() != null) {
                where.add(cb.equal(root.get("enabled"), filter.active()));
            }
            if (filter.search() != null && !filter.search().isBlank()) {
                String pattern = Like.contains(filter.search());
                Subquery<Long> store = query.subquery(Long.class);
                Root<SellerProfile> profile = store.from(SellerProfile.class);
                store.select(profile.get("userId")).where(
                        cb.equal(profile.get("userId"), root.get("id")),
                        cb.like(cb.lower(profile.get("storeName")), pattern, '\\'));
                where.add(cb.or(
                        cb.like(cb.lower(root.get("username")), pattern, '\\'),
                        cb.like(cb.lower(root.get("email")), pattern, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("fullName"), "")), pattern, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("phone"), "")), pattern, '\\'),
                        cb.exists(store)));
            }
            if (filter.joinedFrom() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
                        filter.joinedFrom().atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            if (filter.joinedTo() != null) {
                where.add(cb.lessThan(root.get("createdAt"),
                        filter.joinedTo().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private Function<User, AdminUserResponse> mapper(List<User> users) {
        Map<Long, SellerProfile> stores = sellerRepository.findAllById(users.stream().map(User::getId).toList())
                .stream().collect(Collectors.toMap(SellerProfile::getUserId, Function.identity()));
        return user -> toResponse(user, stores.get(user.getId()));
    }

    static AdminUserResponse toResponse(User user, SellerProfile store) {
        UserResponse base = UserResponse.from(user, store);
        return new AdminUserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getFullName(),
                user.getPhone(), user.getRole().name(), user.isEnabled(), user.getSuspensionReason(),
                user.getStatusChangedAt(), user.getCreatedAt(), user.getLastLoginAt(), base.address(), base.store());
    }

    /** Used by the security log search. */
    public static Specification<LoginEvent> loginSpecification(Boolean success, Role portal, String search,
                                                              LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (success != null) {
                where.add(cb.equal(root.get("success"), success));
            }
            if (portal != null) {
                where.add(cb.equal(root.get("portal"), portal));
            }
            if (search != null && !search.isBlank()) {
                String pattern = Like.contains(search);
                where.add(cb.or(cb.like(cb.lower(root.get("username")), pattern, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("ipAddress"), "")), pattern, '\\')));
            }
            if (from != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            if (to != null) {
                where.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }
}
