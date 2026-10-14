package com.enterprise.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Full Spring context + H2 + MockMvc: sign-up, sign-in per side of the marketplace, account, stores, JWKS. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.store").doesNotExist());
    }

    @Test
    void sellerRegistersWithAStoreThatAppearsInTheDirectory() throws Exception {
        String body = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"SELLER\",\"username\":\"desertdeals\",\"email\":\"hi@desert.ae\","
                                + "\"password\":\"Passw0rd!\",\"phone\":\"+971501234567\",\"storeName\":\"Desert Deals\","
                                + "\"city\":\"Dubai\",\"vatNumber\":\"100123456700003\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("SELLER"))
                .andExpect(jsonPath("$.user.store.storeName").value("Desert Deals"))
                .andExpect(jsonPath("$.user.store.vatNumber").value("100123456700003"))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");
        String claims = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        assertThat(claims).contains("\"storeName\":\"Desert Deals\"").contains("\"role\":\"SELLER\"");

        mockMvc.perform(get("/api/sellers").param("search", "desert"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].storeName").value("Desert Deals"));
        // a seller signs in to Seller Central, not to the customer side
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"desertdeals\",\"password\":\"Passw0rd!\",\"accountType\":\"CUSTOMER\"}"))
                .andExpect(status().isForbidden());
        login("desertdeals", "Passw0rd!", "SELLER");
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"customer\",\"password\":\"Customer@12345\",\"accountType\":\"SELLER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("This is a customer account. Please use the customer sign-in."));
    }

    @Test
    void storeSearchTreatsWildcardsAsText() throws Exception {
        mockMvc.perform(get("/api/sellers").param("search", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/sellers").param("search", "demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].storeName").value("Demo Store"));
    }

    @Test
    void customerSavesAddressAndChangesPassword() throws Exception {
        String body = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"CUSTOMER\",\"username\":\"omar\",\"email\":\"omar@example.com\",\"password\":\"Passw0rd1\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String auth = "Bearer " + JsonPath.read(body, "$.accessToken");

        mockMvc.perform(put("/api/users/me/address").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient\":\"Omar Ali\",\"phone\":\"+971501112233\",\"line1\":\"Flat 1204, Marina Heights\","
                                + "\"city\":\"Dubai\",\"region\":\"Dubai\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address.country").value("United Arab Emirates"))
                .andExpect(jsonPath("$.address.formatted").value(
                        "Omar Ali, Flat 1204, Marina Heights, Dubai, Dubai, United Arab Emirates (+971501112233)"));

        mockMvc.perform(put("/api/users/me/password").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-one\",\"newPassword\":\"NewPassw0rd\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The current password is not correct"));
        mockMvc.perform(put("/api/users/me/password").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Passw0rd1\",\"newPassword\":\"NewPassw0rd\"}"))
                .andExpect(status().isNoContent());
        login("omar", "NewPassw0rd", "CUSTOMER");
    }

    @Test
    void sellerUpdatesStoreDetails() throws Exception {
        String auth = "Bearer " + login("seller", "Seller@12345", "SELLER");
        mockMvc.perform(put("/api/users/me").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Demo Seller\",\"storeDescription\":\"Electronics and home\","
                                + "\"supportEmail\":\"help@demo.ae\",\"vatNumber\":\"12345\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.vatNumber").value("must be the 15-digit TRN"));
        mockMvc.perform(put("/api/users/me").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Demo Seller\",\"storeDescription\":\"Electronics and home\","
                                + "\"supportEmail\":\"help@demo.ae\",\"vatNumber\":\"100000000000003\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.store.description").value("Electronics and home"))
                .andExpect(jsonPath("$.store.supportEmail").value("help@demo.ae"));
    }

    @Test
    void weakPasswordIsRejectedWithFieldError() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"CUSTOMER\",\"username\":\"bob\",\"email\":\"bob@example.com\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
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
