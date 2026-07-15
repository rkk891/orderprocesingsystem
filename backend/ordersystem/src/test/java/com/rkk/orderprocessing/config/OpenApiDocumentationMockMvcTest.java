package com.rkk.orderprocessing.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rkk.orderprocessing.order.api.ApiMapper;
import com.rkk.orderprocessing.order.api.OrderController;
import com.rkk.orderprocessing.order.application.OrderService;
import com.rkk.orderprocessing.shared.api.ApiExceptionHandler;
import com.rkk.orderprocessing.shared.api.OpenApiDocumentController;
import com.rkk.orderprocessing.shared.api.RequestTraceFilter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.yaml.snakeyaml.Yaml;

/** Verifies that the checked-in OpenAPI contract and Swagger UI stay accurate and reachable. */
@WebMvcTest(OrderController.class)
@ImportAutoConfiguration({
    SpringDocConfiguration.class,
    SpringDocConfigProperties.class,
    SpringDocWebMvcConfiguration.class,
    SwaggerConfig.class,
    SwaggerUiConfigProperties.class,
    SwaggerUiOAuthProperties.class
})
@Import({ApiMapper.class, ApiExceptionHandler.class, OpenApiDocumentController.class, RequestTraceFilter.class})
class OpenApiDocumentationMockMvcTest {

    @MockitoBean
    private OrderService service;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void staticOpenApiDocumentMatchesTheNonStandardHttpRules() throws Exception {
        MvcResult result = mockMvc.perform(get("/openapi.yaml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/yaml"))
                .andReturn();

        Map<String, Object> document = new Yaml().load(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        Map<String, Object> paths = map(document.get("paths"));
        assertThat(document.get("openapi")).isEqualTo("3.1.0");
        assertThat(paths).containsOnlyKeys(
                "/api/v1/orders",
                "/api/v1/orders/{orderId}",
                "/api/v1/orders/{orderId}/status",
                "/api/v1/orders/{orderId}/cancel");

        Map<String, Object> orders = map(paths.get("/api/v1/orders"));
        assertThat(orders).containsOnlyKeys("post", "get");
        Map<String, Object> create = map(orders.get("post"));
        assertConsumerFacingDescription(
                create, "Create a new order", "ready to save a new order", "201 Created");
        assertResponseCodes(create, "201", "400", "406", "415", "500");
        assertThat(exampleRef(create, "201")).isEqualTo("#/components/examples/PendingOrder");
        assertThat(map(map(create.get("responses")).get("201")).get("headers")).asString()
                .contains("Location", "X-Trace-Id");

        Map<String, Object> listOrders = map(orders.get("get"));
        assertConsumerFacingDescription(
                listOrders, "Browse and filter orders", "order history", "empty `content`");
        assertThat(exampleRef(listOrders, "200")).isEqualTo("#/components/examples/DemoOrderPage");
        List<Map<String, Object>> listParameters = listOfMaps(listOrders.get("parameters"));
        assertThat(listParameters).extracting(parameter -> parameter.get("name"))
                .containsExactly("status", "page", "size");
        Map<String, Map<String, Object>> parametersByName = listParameters.stream()
                .collect(Collectors.toMap(parameter -> (String) parameter.get("name"), parameter -> parameter));
        assertThat(map(parametersByName.get("page").get("schema")))
                .containsEntry("minimum", 0)
                .containsEntry("default", 0);
        assertThat(map(parametersByName.get("size").get("schema")))
                .containsEntry("minimum", 1)
                .containsEntry("maximum", 100)
                .containsEntry("default", 20);

        Map<String, Object> detailPath = map(paths.get("/api/v1/orders/{orderId}"));
        assertThat(detailPath).containsOnlyKeys("get");
        Map<String, Object> getOrder = map(detailPath.get("get"));
        assertConsumerFacingDescription(
                getOrder, "Find one order by ID", "complete current order", "does not exist");
        assertResponseCodes(getOrder, "200", "400", "404", "406", "500");
        assertThat(exampleRef(getOrder, "200")).isEqualTo("#/components/examples/PendingOrder");

        Map<String, Object> statusPath = map(paths.get("/api/v1/orders/{orderId}/status"));
        assertThat(statusPath).containsOnlyKeys("patch");
        Map<String, Object> updateStatus = map(statusPath.get("patch"));
        assertConsumerFacingDescription(
                updateStatus, "Move an order to its next status", "fulfilment", "409");
        assertThat(map(updateStatus.get("requestBody"))).containsEntry("required", true);
        assertResponseCodes(updateStatus, "200", "400", "404", "409", "406", "415", "500");
        assertThat(exampleRef(updateStatus, "200")).isEqualTo("#/components/examples/ProcessingOrder");

        Map<String, Object> cancel = map(map(paths.get("/api/v1/orders/{orderId}/cancel")).get("post"));
        assertConsumerFacingDescription(
                cancel, "Cancel an order before processing", "stopped before processing", "never deletes");
        assertThat(cancel).doesNotContainKey("requestBody");
        assertResponseCodes(cancel, "200", "400", "404", "409", "406", "500");
        assertThat(exampleRef(cancel, "200")).isEqualTo("#/components/examples/CancelledOrder");

        Map<String, Object> components = map(document.get("components"));
        Map<String, Object> schemas = map(components.get("schemas"));
        Map<String, Object> createSchema = map(schemas.get("NewOrderRequest"));
        assertThat(createSchema.get("additionalProperties")).isEqualTo(false);
        assertThat(map(createSchema.get("example"))).containsKey("items");
        assertThat(map(map(createSchema.get("properties")).get("items")))
                .containsEntry("minItems", 1)
                .containsEntry("maxItems", 100);
        Map<String, Object> problem = map(schemas.get("ProblemDetails"));
        assertThat(problem.get("additionalProperties")).isEqualTo(false);
        assertThat(list(problem.get("required"))).containsExactlyInAnyOrder(
                "type", "title", "status", "detail", "instance", "code", "traceId", "violations");
        assertThat(map(schemas.get("UpdateStatusRequest"))).containsKey("example");
        assertThat(map(map(components.get("parameters")).get("OrderId")))
                .containsEntry("example", "11111111-1111-4111-8111-111111111111");
        Map<String, Object> examples = map(components.get("examples"));
        assertThat(map(map(examples.get("ProcessingOrder")).get("value")))
                .containsEntry("status", "PROCESSING");
        assertThat(map(map(examples.get("CancelledOrder")).get("value")))
                .containsEntry("status", "CANCELLED");
        assertThat(list(map(map(examples.get("DemoOrderPage")).get("value")).get("content")))
                .hasSize(5);
        assertProblemExample(examples, "InvalidRequestProblem", 400, "INVALID_REQUEST");
        assertProblemExample(examples, "OrderNotFoundProblem", 404, "ORDER_NOT_FOUND");
        assertProblemExample(examples, "OrderStateConflictProblem", 409, "ORDER_STATE_CONFLICT");
        assertProblemExample(examples, "NotAcceptableProblem", 406, "NOT_ACCEPTABLE");
        assertProblemExample(examples, "UnsupportedMediaTypeProblem", 415, "UNSUPPORTED_MEDIA_TYPE");
        assertProblemExample(examples, "InternalErrorProblem", 500, "INTERNAL_ERROR");
        assertThat(map(map(map(components.get("responses")).get("InvalidRequest")).get("content")))
                .containsOnlyKeys("application/problem+json");
    }

    @Test
    void swaggerUiEntryPointRedirectsToTheBundledUi() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Swagger UI")));
    }

    @Test
    void swaggerConfigurationUsesTheCheckedInContractAndRuntimeGenerationIsDisabled() throws Exception {
        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/openapi.yaml"));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return (List<Object>) value;
    }

    private static void assertResponseCodes(Map<String, Object> operation, String... expectedCodes) {
        assertThat(map(operation.get("responses"))).containsOnlyKeys(expectedCodes);
    }

    private static String exampleRef(Map<String, Object> operation, String responseCode) {
        Map<String, Object> response = map(map(operation.get("responses")).get(responseCode));
        Map<String, Object> content = map(response.get("content"));
        Map<String, Object> mediaType = map(content.get("application/json"));
        Map<String, Object> examples = map(mediaType.get("examples"));
        return (String) map(examples.values().iterator().next()).get("$ref");
    }

    private static void assertProblemExample(
            Map<String, Object> examples, String name, int status, String code) {
        assertThat(map(map(examples.get(name)).get("value")))
                .containsEntry("status", status)
                .containsEntry("code", code);
    }

    /** Keeps every rendered operation useful to a reader instead of exposing only protocol details. */
    private static void assertConsumerFacingDescription(
            Map<String, Object> operation,
            String expectedSummary,
            String expectedPurpose,
            String expectedOutcome) {
        assertThat(operation.get("summary")).isEqualTo(expectedSummary);
        String normalizedDescription = operation.get("description").toString()
                .replaceAll("\\s+", " ")
                .trim();
        assertThat(normalizedDescription)
                .contains("**Use this when:**", "**What it does:**", "**What to expect:**")
                .contains(expectedPurpose, expectedOutcome);
    }
}
