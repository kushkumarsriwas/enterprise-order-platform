package com.enterprise.auth.user;

import com.enterprise.auth.common.ApiException;
import com.enterprise.auth.common.PageResponse;
import com.enterprise.auth.user.UserResponse.StoreResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public store directory of the marketplace. */
@RestController
@RequestMapping("/api/merchants")
@Tag(name = "Stores")
public class StoreController {

    private final MerchantProfileRepository merchantRepository;

    public StoreController(MerchantProfileRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Active stores on the marketplace (public)")
    public PageResponse<StoreResponse> list(@RequestParam(defaultValue = "") String search,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "24") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("storeName"));
        return PageResponse.from(merchantRepository.searchActive(search.trim(), pageable), StoreResponse::from);
    }

    @GetMapping("/{merchantId}")
    @Transactional(readOnly = true)
    @Operation(summary = "One store's public profile")
    public StoreResponse get(@PathVariable Long merchantId) {
        return merchantRepository.findByUserId(merchantId)
                .filter(profile -> profile.getUser().isEnabled())
                .map(StoreResponse::from)
                .orElseThrow(() -> ApiException.notFound("Store not found"));
    }
}
