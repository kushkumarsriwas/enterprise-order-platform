package com.enterprise.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.notification.support.TestTokens;
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

    private void create(String json) throws Exception {
        mockMvc.perform(post("/internal/notifications").header("Authorization", "Bearer " + TestTokens.customer(1))
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
        mockMvc.perform(post("/internal/notifications").header("Authorization", "Bearer " + TestTokens.customer(1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audience\":\"USER\",\"type\":\"X\",\"title\":\"t\",\"message\":\"m\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anonymousCallsAreRejected() throws Exception {
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    }
}
