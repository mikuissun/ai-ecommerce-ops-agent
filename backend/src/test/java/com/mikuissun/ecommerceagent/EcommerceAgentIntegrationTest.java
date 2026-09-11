package com.mikuissun.ecommerceagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EcommerceAgentIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void registerAndLoginReturnJwt() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"StrongPass1\",\"name\":\"Test User\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"StrongPass1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email", is(email)));
    }

    @Test
    void businessEndpointsRequireJwt() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void productIsolationAndListFiltersAreEnforced() throws Exception {
        String demoToken = login("demo@example.com", "password");
        String otherToken = registerAndGetToken(uniqueEmail());

        mockMvc.perform(get("/api/products").header("Authorization", bearer(demoToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(12)));
        mockMvc.perform(get("/api/products").param("marketplace", "Amazon US")
                        .header("Authorization", bearer(demoToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(4)));
        mockMvc.perform(get("/api/products/1").header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void inventoryIsolationAndLowStockWork() throws Exception {
        String demoToken = login("demo@example.com", "password");
        String otherToken = registerAndGetToken(uniqueEmail());

        mockMvc.perform(get("/api/inventory").param("lowStock", "true")
                        .header("Authorization", bearer(demoToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(5)));
        mockMvc.perform(get("/api/inventory/1").header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void orderIsolationAndDateFilteringWork() throws Exception {
        String demoToken = login("demo@example.com", "password");
        String otherToken = registerAndGetToken(uniqueEmail());

        mockMvc.perform(get("/api/orders").param("startDate", "2026-09-04").param("endDate", "2026-09-10")
                        .header("Authorization", bearer(demoToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(8)));
        mockMvc.perform(get("/api/orders/1").header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void dashboardSummaryContainsOperationalMetrics() throws Exception {
        String token = login("demo@example.com", "password");
        mockMvc.perform(get("/api/dashboard/summary").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productCount", is(12)))
                .andExpect(jsonPath("$.data.lowStockCount", is(5)))
                .andExpect(jsonPath("$.data.orderCount", is(30)))
                .andExpect(jsonPath("$.data.refundCount", is(3)))
                .andExpect(jsonPath("$.data.completedOrderCount", is(15)));
    }

    @Test
    void invalidJwtIsRejected() throws Exception {
        mockMvc.perform(get("/api/products").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    private String login(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    private String registerAndGetToken(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"StrongPass1\",\"name\":\"Other User\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(response).path("data");
        return data.path("accessToken").asText();
    }

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private String bearer(String token) { return "Bearer " + token; }
}
