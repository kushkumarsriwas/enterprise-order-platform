package com.enterprise.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.notification.security.InternalApiKey;
import com.enterprise.notification.support.TestTokens;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestTokens.class)
class NotificationApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String KEY = "test-internal-key";

    private void create(String json) throws Exception {
        mockMvc.perform(post("/internal/notifications").header(InternalApiKey.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    @Test
    void usersSeeOnlyTheirOwnNotificationsAndAdminsSeeAlerts() throws Exception {
        create("{\"userId\":21,\"audience\":\"USER\",\"type\":\"ORDER_PLACED\",\"title\":\"Order placed\",\"message\":\"Thanks!\"}");
        create("{\"userId\":22,\"audience\":\"USER\",\"type\":\"ORDER_PLACED\",\"title\":\"Other\",\"message\":\"Not yours\"}");
        create("{\"audience\":\"ADMINS\",\"type\":\"LOW_STOCK\",\"title\":\"Low stock\",\"message\":\"Restock soon\"}");

        String user21 = "Bearer " + TestTokens.customer(21);
        mockMvc.perform(get("/api/notifications").header("Authorization", user21))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Order placed"));
        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", user21))
                .andExpect(jsonPath("$.unread").value(1));
        mockMvc.perform(patch("/api/notifications/read-all").header("Authorization", user21))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", user21))
                .andExpect(jsonPath("$.unread").value(0));

        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + TestTokens.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("LOW_STOCK"));
    }

    @Test
    void userNotificationNeedsARecipient() throws Exception {
        mockMvc.perform(post("/internal/notifications").header(InternalApiKey.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audience\":\"USER\",\"type\":\"X\",\"title\":\"t\",\"message\":\"m\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyOtherServicesCanCreateNotifications() throws Exception {
        String json = "{\"userId\":5,\"audience\":\"USER\",\"type\":\"FAKE\",\"title\":\"You won\",\"message\":\"m\"}";
        mockMvc.perform(post("/internal/notifications").header("Authorization", "Bearer " + TestTokens.customer(1))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/internal/notifications").header(InternalApiKey.HEADER, "guess")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isForbidden());
    }

    @Test
    void notificationsWithAnAddressAreAlsoEmailedThroughTheOutbox() throws Exception {
        create("{\"userId\":25,\"audience\":\"USER\",\"type\":\"ORDER_SHIPPED\",\"title\":\"Order shipped\","
                + "\"message\":\"Aramex AWB-1\",\"email\":\"kush@example.com\"}");
        String admin = "Bearer " + TestTokens.admin();
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mockMvc.perform(get("/api/admin/emails").param("q", "kush@").header("Authorization", admin))
                        .andExpect(jsonPath("$.content[0].status").value("SENT"))
                        .andExpect(jsonPath("$.content[0].subject").value("Order shipped")));
        mockMvc.perform(get("/api/admin/notifications").param("userId", "25").header("Authorization", admin))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("ORDER_SHIPPED"));
        mockMvc.perform(get("/api/admin/emails").header("Authorization", "Bearer " + TestTokens.customer(25)))
                .andExpect(status().isForbidden());
    }

    @Test
    void announcementsReachTheRightAudience() throws Exception {
        String admin = "Bearer " + TestTokens.admin();
        mockMvc.perform(post("/api/admin/announcements").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"White Friday\",\"message\":\"Up to 70% off\",\"audience\":\"CUSTOMERS\","
                                + "\"level\":\"SUCCESS\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/admin/announcements").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Payout delay\",\"message\":\"Bank holiday\",\"audience\":\"SELLERS\","
                                + "\"level\":\"WARNING\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/announcements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/announcements").header("Authorization", "Bearer " + TestTokens.customer(3)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("White Friday"));
        mockMvc.perform(get("/api/announcements").header("Authorization", "Bearer " + TestTokens.seller(4)))
                .andExpect(jsonPath("$[0].title").value("Payout delay"));
        mockMvc.perform(post("/api/admin/announcements").header("Authorization", "Bearer " + TestTokens.seller(4))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"message\":\"y\",\"audience\":\"EVERYONE\",\"level\":\"INFO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCallsAreRejected() throws Exception {
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    }
}
