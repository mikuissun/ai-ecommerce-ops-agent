package com.mikuissun.ecommerceagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikuissun.ecommerceagent.common.CurrentUserContext;
import com.mikuissun.ecommerceagent.dto.auth.RegisterRequest;
import com.mikuissun.ecommerceagent.dto.inventory.InventoryToolResponse;
import com.mikuissun.ecommerceagent.dto.product.ProductResponse;
import com.mikuissun.ecommerceagent.entity.InventoryEntity;
import com.mikuissun.ecommerceagent.entity.OrderEntity;
import com.mikuissun.ecommerceagent.entity.ProductEntity;
import com.mikuissun.ecommerceagent.entity.UserEntity;
import com.mikuissun.ecommerceagent.mapper.InventoryMapper;
import com.mikuissun.ecommerceagent.mapper.OrderMapper;
import com.mikuissun.ecommerceagent.mapper.ProductMapper;
import com.mikuissun.ecommerceagent.service.UserService;
import com.mikuissun.ecommerceagent.tool.Tool;
import com.mikuissun.ecommerceagent.tool.ToolDefinition;
import com.mikuissun.ecommerceagent.tool.ToolExecutor;
import com.mikuissun.ecommerceagent.tool.ToolRegistry;
import com.mikuissun.ecommerceagent.tool.ToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ToolSystemIntegrationTest {
    @Autowired ToolRegistry toolRegistry;
    @Autowired ToolExecutor toolExecutor;
    @Autowired UserService userService;
    @Autowired ProductMapper productMapper;
    @Autowired InventoryMapper inventoryMapper;
    @Autowired OrderMapper orderMapper;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @AfterEach
    void clearUserContext() { CurrentUserContext.clear(); }

    @Test
    void registryDiscoversAllReadOnlyToolsAndDefinitions() {
        assertEquals(7, toolRegistry.size());
        assertEquals(List.of("get_inventory", "get_product", "get_sales_summary", "list_low_stock_products",
                "query_orders", "search_products", "update_product_price"), toolRegistry.definitions().stream().map(ToolDefinition::name).toList());
        ToolDefinition definition = toolRegistry.find("get_product").orElseThrow().definition();
        assertEquals("根据 SKU 查询当前用户商品", definition.description());
        assertEquals("sku", definition.parameters().get(0).name());
        assertTrue(definition.parameters().get(0).required());
    }

    @Test
    void registryRejectsDuplicateToolNames() {
        Tool duplicateOne = tool("duplicate_tool");
        Tool duplicateTwo = tool("duplicate_tool");
        assertThrows(IllegalStateException.class, () -> new ToolRegistry(List.of(duplicateOne, duplicateTwo)));
    }

    @Test
    void executorReturnsClearUnknownAndParameterErrors() {
        assertEquals("TOOL_NOT_FOUND", toolExecutor.execute("not_a_tool", Map.of()).errorCode());
        executeAs(1L, "get_product", Map.of());
        assertEquals("MISSING_PARAMETER", toolExecutor.execute("get_product", Map.of()).errorCode());
        assertEquals("INVALID_PARAMETER", toolExecutor.execute("get_sales_summary", Map.of("days", "abc")).errorCode());
        assertEquals("PARAMETER_OUT_OF_RANGE", toolExecutor.execute("get_sales_summary", Map.of("days", 500)).errorCode());
        assertEquals("UNKNOWN_PARAMETER", toolExecutor.execute("get_product", Map.of("userId", 1, "sku", "SKU-A001")).errorCode());
    }

    @Test
    void getProductToolReturnsCurrentUsersProduct() {
        ToolResult result = executeAs(1L, "get_product", Map.of("sku", "SKU-A001"));
        ProductResponse product = assertInstanceOf(ProductResponse.class, result.data());
        assertTrue(result.success());
        assertEquals("SKU-A001", product.sku());
        assertEquals("Amazon US", product.marketplace());
    }

    @Test
    void searchProductsToolUsesBoundedServiceQuery() {
        ToolResult result = executeAs(1L, "search_products", Map.of("marketplace", "Amazon US"));
        List<?> products = assertInstanceOf(List.class, result.data());
        assertEquals(4, products.size());
    }

    @Test
    void getInventoryToolReturnsStockSummary() {
        ToolResult result = executeAs(1L, "get_inventory", Map.of("sku", "SKU-A002"));
        InventoryToolResponse inventory = assertInstanceOf(InventoryToolResponse.class, result.data());
        assertEquals("SKU-A002", inventory.sku());
        assertEquals(5, inventory.availableQuantity());
        assertTrue(inventory.lowStock());
    }

    @Test
    void listLowStockToolReturnsLowStockProducts() {
        ToolResult result = executeAs(1L, "list_low_stock_products", Map.of());
        List<?> products = assertInstanceOf(List.class, result.data());
        assertEquals(5, products.size());
    }

    @Test
    void queryOrdersToolSupportsDateAndStatusFilters() {
        ToolResult result = executeAs(1L, "query_orders", Map.of(
                "startDate", "2026-09-04", "endDate", "2026-09-10"));
        List<?> orders = assertInstanceOf(List.class, result.data());
        assertEquals(8, orders.size());
        assertEquals("INVALID_PARAMETER", toolExecutor.execute("query_orders", Map.of(
                "startDate", "2026-09-10", "endDate", "2026-09-01")).errorCode());
    }

    @Test
    void salesSummaryToolReturnsStructuredAnalytics() {
        ToolResult result = executeAs(1L, "get_sales_summary", Map.of("days", 90));
        assertTrue(result.success());
        JsonNode json = objectMapper.valueToTree(result.data());
        assertEquals(30, json.path("orderCount").asInt());
        assertEquals(15, json.path("completedOrderCount").asInt());
        assertEquals(3, json.path("refundCount").asInt());
        assertTrue(json.path("topSellingProducts").size() > 0);
    }

    @Test
    void debugApiRequiresJwtAndExecutesTool() throws Exception {
        mockMvc.perform(get("/api/tools")).andExpect(status().isUnauthorized());
        String token = login("demo@example.com", "password");
        mockMvc.perform(get("/api/tools").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(7)));
        mockMvc.perform(post("/api/tools/get_product/execute")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"arguments\":{\"sku\":\"SKU-A001\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.data.sku").value("SKU-A001"));
    }

    @Test
    void toolsCannotReadAnotherUsersProductInventoryOrdersOrSummary() {
        UserEntity otherUser = createUserWithBusinessData();
        String otherSku = productMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ProductEntity>()
                .eq("user_id", otherUser.getId())).get(0).getSku();

        ToolResult productResult = executeAs(1L, "get_product", Map.of("sku", otherSku));
        ToolResult inventoryResult = executeAs(1L, "get_inventory", Map.of("sku", otherSku));
        ToolResult orderResult = executeAs(1L, "query_orders", Map.of("status", "PENDING"));
        ToolResult summaryResult = executeAs(otherUser.getId(), "get_sales_summary", Map.of("days", 90));

        assertFalse(productResult.success());
        assertEquals("NOT_FOUND", productResult.errorCode());
        assertFalse(inventoryResult.success());
        assertEquals("NOT_FOUND", inventoryResult.errorCode());
        assertTrue(orderResult.success());
        assertEquals(0, assertInstanceOf(List.class, orderResult.data()).size());
        assertTrue(summaryResult.success());
        assertEquals(1, objectMapper.valueToTree(summaryResult.data()).path("orderCount").asInt());
    }

    private ToolResult executeAs(long userId, String toolName, Map<String, Object> arguments) {
        CurrentUserContext.setUserId(userId);
        return toolExecutor.execute(toolName, arguments);
    }

    private Tool tool(String name) {
        return new Tool() {
            @Override public ToolDefinition definition() { return new ToolDefinition(name, "test", List.of()); }
            @Override public ToolResult execute(com.mikuissun.ecommerceagent.tool.ToolArguments arguments) {
                return ToolResult.success(Map.of());
            }
        };
    }

    private UserEntity createUserWithBusinessData() {
        UserEntity user = userService.register(new RegisterRequest(uniqueEmail(), "StrongPass1", "Tool Test User"));
        ProductEntity product = new ProductEntity();
        product.setUserId(user.getId());
        product.setSku("USER-B-" + UUID.randomUUID().toString().substring(0, 8));
        product.setName("User B Product");
        product.setMarketplace("Shopify");
        product.setCategory("Test");
        product.setPrice(new BigDecimal("12.00"));
        product.setCurrency("USD");
        product.setStatus("ACTIVE");
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.insert(product);

        InventoryEntity inventory = new InventoryEntity();
        inventory.setUserId(user.getId());
        inventory.setProductId(product.getId());
        inventory.setAvailableQuantity(1);
        inventory.setReservedQuantity(0);
        inventory.setReorderThreshold(3);
        inventory.setUpdatedAt(LocalDateTime.now());
        inventoryMapper.insert(inventory);

        OrderEntity order = new OrderEntity();
        order.setUserId(user.getId());
        order.setOrderNo("USER-B-" + UUID.randomUUID());
        order.setMarketplace("Shopify");
        order.setCustomerCountry("US");
        order.setStatus("PENDING");
        order.setTotalAmount(new BigDecimal("12.00"));
        order.setCurrency("USD");
        order.setOrderedAt(LocalDateTime.now());
        order.setCreatedAt(LocalDateTime.now());
        orderMapper.insert(order);
        assertNotNull(order.getId());
        return user;
    }

    private String uniqueEmail() { return "tool-user-" + UUID.randomUUID() + "@example.com"; }

    private String login(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }
}
