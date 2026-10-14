package com.enterprise.auth.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.auth.client.CatalogClient;
import com.enterprise.auth.common.ApiException;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The single platform administrator: separate sign-in, account oversight, suspension and the security log. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private CatalogClient catalogClient;

    private String adminToken() throws Exception {
        String body = mockMvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin-Test-Pass1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.expiresIn").value(7200))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    private long registerSeller(String username, String store) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"SELLER\",\"username\":\"" + username + "\",\"email\":\"" + username
                                + "@stores.ae\",\"password\":\"Passw0rd!\",\"phone\":\"+971501234000\",\"storeName\":\""
                                + store + "\",\"city\":\"Sharjah\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.user.id")).longValue();
    }

    @Test
    void theAdminSignsInOnlyThroughTheAdminConsole() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin-Test-Pass1\",\"accountType\":\"SELLER\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
        mockMvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"customer\",\"password\":\"Customer@12345\"}"))
                .andExpect(status().isUnauthorized());
        adminToken();
    }

    @Test
    void customersAndSellersCannotUseAdminEndpoints() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"seller\",\"password\":\"Seller@12345\",\"accountType\":\"SELLER\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + JsonPath.read(body, "$.accessToken")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void theAdminSearchesAccountsAndSeesEverything() throws Exception {
        String admin = adminToken();
        registerSeller("palmtrading", "Palm Trading");

        mockMvc.perform(get("/api/admin/users").param("role", "SELLER").param("search", "palm")
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].store.storeName").value("Palm Trading"))
                .andExpect(jsonPath("$.content[0].email").value("palmtrading@stores.ae"));
        // the admin account itself is never listed
        String all = mockMvc.perform(get("/api/admin/users").param("size", "100").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(all).doesNotContain("\"role\":\"ADMIN\"");

        mockMvc.perform(get("/api/admin/users/stats").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signupsLast30Days.length()").value(30));
        mockMvc.perform(get("/api/admin/users/export/xlsx").param("role", "SELLER").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("sellers-")));
    }

    @Test
    void suspendingASellerBlocksSignInAndHidesTheStore() throws Exception {
        String admin = adminToken();
        long sellerId = registerSeller("oasis", "Oasis Outlet");

        mockMvc.perform(put("/api/admin/users/" + sellerId + "/status").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false,\"reason\":\"Counterfeit goods\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.suspensionReason").value("Counterfeit goods"));
        verify(catalogClient).setStoreSuspended(sellerId, true);
        mockMvc.perform(get("/api/sellers/" + sellerId)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"oasis\",\"password\":\"Passw0rd!\",\"accountType\":\"SELLER\"}"))
                .andExpect(status().isForbidden());

        String detail = mockMvc.perform(get("/api/admin/users/" + sellerId).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.store.storeName").value("Oasis Outlet"))
                .andReturn().getResponse().getContentAsString();
        assertThat(detail).contains("Account suspended");

        doThrow(ApiException.serviceUnavailable("down")).when(catalogClient).setStoreSuspended(anyLong(), anyBoolean());
        mockMvc.perform(put("/api/admin/users/" + sellerId + "/status").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(
                        "The account was reactivated, but the store's listings could not be updated yet. Please try again."));
    }

    @Test
    void failedSignInsAppearInTheSecurityLog() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.9, 10.0.0.1")
                        .content("{\"username\":\"intruder\",\"password\":\"guess1234\",\"accountType\":\"CUSTOMER\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/security/logins").param("success", "false").param("search", "203.0.113")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("intruder"))
                .andExpect(jsonPath("$.content[0].failureReason").value("Unknown username"))
                .andExpect(jsonPath("$.content[0].ipAddress").value("203.0.113.9"));
    }
}
