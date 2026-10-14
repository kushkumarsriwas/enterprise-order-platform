package com.enterprise.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.auth.client.CatalogClient;
import com.enterprise.auth.common.ApiException;
import com.jayway.jsonpath.JsonPath;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Full Spring context + H2: customer and merchant sign-up, portals, JWKS, stores, admin. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private CatalogClient catalogClient;

    private String login(String username, String password, String accountType) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password
                                + "\",\"accountType\":\"" + accountType + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    @Test
    void customerRegistersAndReadsOwnProfile() throws Exception {
        String body = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"CUSTOMER\",\"username\":\"alice\",\"email\":\"alice@example.com\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void merchantRegistersWithAStoreThatAppearsInTheDirectory() throws Exception {
        String body = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"MERCHANT\",\"username\":\"desertdeals\",\"email\":\"hi@desert.ae\","
                                + "\"password\":\"Passw0rd!\",\"phone\":\"+971501234567\",\"storeName\":\"Desert Deals\","
                                + "\"city\":\"Dubai\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("MERCHANT"))
                .andExpect(jsonPath("$.user.store.storeName").value("Desert Deals"))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");
        String claims = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        assertThat(claims).contains("\"storeName\":\"Desert Deals\"").contains("\"role\":\"MERCHANT\"");

        mockMvc.perform(get("/api/merchants").param("search", "desert"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].storeName").value("Desert Deals"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"desertdeals\",\"password\":\"Passw0rd!\",\"accountType\":\"CUSTOMER\"}"))
                .andExpect(status().isForbidden());
        login("desertdeals", "Passw0rd!", "MERCHANT");
    }

    @Test
    void weakPasswordIsRejectedWithFieldError() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"CUSTOMER\",\"username\":\"bob\",\"email\":\"bob@example.com\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void adminManagesAccounts() throws Exception {
        String customerToken = login("customer", "Customer@12345", "CUSTOMER");
        String adminToken = login("admin", "Admin@12345", "MERCHANT");

        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
        String users = mockMvc.perform(get("/api/users").param("role", "CUSTOMER")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer customerId = JsonPath.read(users, "$.content[?(@.username == 'customer')].id[0]") instanceof Integer i
                ? i : ((net.minidev.json.JSONArray) JsonPath.read(users, "$.content[?(@.username == 'customer')].id")).stream()
                .map(Integer.class::cast).findFirst().orElseThrow();
        mockMvc.perform(put("/api/users/" + customerId + "/status").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"customer\",\"password\":\"Customer@12345\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/users/" + customerId + "/status").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk());
    }

    @Test
    void suspendingAMerchantHidesTheStoreAndItsProducts() throws Exception {
        String body = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"MERCHANT\",\"username\":\"oasis\",\"email\":\"hi@oasis.ae\","
                                + "\"password\":\"Passw0rd!\",\"phone\":\"+971501234000\",\"storeName\":\"Oasis Outlet\","
                                + "\"city\":\"Sharjah\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long merchantId = ((Number) JsonPath.read(body, "$.user.id")).longValue();
        String admin = "Bearer " + login("admin", "Admin@12345", "MERCHANT");

        mockMvc.perform(put("/api/users/" + merchantId + "/status").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());
        verify(catalogClient).setMerchantActive(merchantId, false);
        mockMvc.perform(get("/api/merchants/" + merchantId)).andExpect(status().isNotFound());

        doThrow(ApiException.serviceUnavailable("down")).when(catalogClient).setMerchantActive(anyLong(), anyBoolean());
        mockMvc.perform(put("/api/users/" + merchantId + "/status").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("The account was reactivated, but the store's products could not be updated yet. Please try again."));
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void jwksPublishesPublicKeyOnly() throws Exception {
        String body = mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("\"d\"");
    }
}
