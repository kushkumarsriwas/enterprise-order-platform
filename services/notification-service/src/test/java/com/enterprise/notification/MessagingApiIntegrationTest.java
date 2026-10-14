package com.enterprise.notification;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.notification.client.StoreDirectoryClient;
import com.enterprise.notification.common.ApiException;
import com.enterprise.notification.support.TestTokens;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** "Contact seller": one thread per customer and store, unread counters and new-message notifications. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestTokens.class)
class MessagingApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private StoreDirectoryClient stores;

    @Test
    void customerAndSellerChat() throws Exception {
        when(stores.storeName(40L)).thenReturn("Gulf Gadgets");
        String customer = "Bearer " + TestTokens.customer(30);
        String merchant = "Bearer " + TestTokens.merchant(40);
        String stranger = "Bearer " + TestTokens.customer(31);

        String started = mockMvc.perform(post("/api/conversations").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"merchantId\":40,\"message\":\"Is the phone still under warranty?\","
                                + "\"productId\":3,\"productName\":\"Galaxy S25\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conversation.merchantName").value("Gulf Gadgets"))
                .andExpect(jsonPath("$.conversation.counterpartName").value("Gulf Gadgets"))
                .andExpect(jsonPath("$.message.mine").value(true))
                .andExpect(jsonPath("$.message.productName").value("Galaxy S25"))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(started, "$.conversation.id");

        // a second "contact seller" continues the same thread and does not look the store up again
        mockMvc.perform(post("/api/conversations").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"merchantId\":40,\"message\":\"And does it come with a charger?\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conversation.id").value(id));
        verify(stores, times(1)).storeName(eq(40L));

        // the seller has 2 unread messages but only 1 notification (no spam)
        mockMvc.perform(get("/api/conversations").header("Authorization", merchant))
                .andExpect(jsonPath("$.content[0].unread").value(2))
                .andExpect(jsonPath("$.content[0].counterpartName").value("customer30"))
                .andExpect(jsonPath("$.content[0].lastMessagePreview").value("And does it come with a charger?"));
        mockMvc.perform(get("/api/conversations/unread-count").header("Authorization", merchant))
                .andExpect(jsonPath("$.unread").value(2));
        mockMvc.perform(get("/api/notifications").header("Authorization", merchant))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("NEW_MESSAGE"))
                .andExpect(jsonPath("$.content[0].title").value("New message from customer30 about Galaxy S25"));

        mockMvc.perform(post("/api/conversations/" + id + "/read").header("Authorization", merchant))
                .andExpect(jsonPath("$.unread").value(0));
        mockMvc.perform(post("/api/conversations/" + id + "/messages").header("Authorization", merchant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Yes, 1 year warranty and a fast charger in the box.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderRole").value("MERCHANT"))
                .andExpect(jsonPath("$.senderName").value("Gulf Gadgets"));

        mockMvc.perform(get("/api/conversations/unread-count").header("Authorization", customer))
                .andExpect(jsonPath("$.unread").value(1));
        mockMvc.perform(get("/api/notifications").header("Authorization", customer))
                .andExpect(jsonPath("$.content[0].title").value("New message from Gulf Gadgets"));
        mockMvc.perform(get("/api/conversations/" + id + "/messages").header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].mine").value(false))
                .andExpect(jsonPath("$.content[2].mine").value(true));

        // nobody else can read or write in the thread
        mockMvc.perform(get("/api/conversations/" + id).header("Authorization", stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/conversations/" + id + "/messages").header("Authorization", "Bearer " + TestTokens.merchant(41)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/conversations/" + id + "/messages").header("Authorization", stranger)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/conversations").header("Authorization", stranger))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void onlyCustomersStartConversationsWithRealStores() throws Exception {
        when(stores.storeName(99L)).thenThrow(ApiException.notFound("Store not found"));
        mockMvc.perform(post("/api/conversations").header("Authorization", "Bearer " + TestTokens.merchant(40))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"merchantId\":41,\"message\":\"hello\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/conversations").header("Authorization", "Bearer " + TestTokens.customer(30))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"merchantId\":99,\"message\":\"hello\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Store not found"));
        mockMvc.perform(post("/api/conversations").header("Authorization", "Bearer " + TestTokens.customer(30))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"merchantId\":40,\"message\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.message").exists());
        mockMvc.perform(get("/api/conversations")).andExpect(status().isUnauthorized());
    }
}
