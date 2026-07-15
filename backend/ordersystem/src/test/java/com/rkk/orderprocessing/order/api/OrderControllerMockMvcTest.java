package com.rkk.orderprocessing.order.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rkk.orderprocessing.order.application.OrderService;
import com.rkk.orderprocessing.order.application.command.CreateOrderData;
import com.rkk.orderprocessing.order.application.exception.InvalidOrderException;
import com.rkk.orderprocessing.order.application.exception.OrderNotFoundException;
import com.rkk.orderprocessing.order.application.exception.OrderStateConflictException;
import com.rkk.orderprocessing.order.application.result.OrderDetails;
import com.rkk.orderprocessing.order.application.result.OrderPage;
import com.rkk.orderprocessing.shared.api.ApiExceptionHandler;
import com.rkk.orderprocessing.shared.api.RequestTraceFilter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.stream.Stream;

/** Checks HTTP validation and the public success and error response shapes. */
@WebMvcTest(OrderController.class)
@Import({ApiMapper.class, ApiExceptionHandler.class, RequestTraceFilter.class})
class OrderControllerMockMvcTest {

    private static final UUID ORDER_ID = UUID.fromString("7bd36f1f-90d4-41dd-8a89-9aa622dfc0ad");
    private static final Instant CREATED = Instant.parse("2026-07-11T12:30:00Z");

    @MockitoBean
    private OrderService service;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createReturns201LocationAndFullDetail() throws Exception {
        when(service.create(any(CreateOrderData.class))).thenReturn(detail("PENDING"));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":"SKU-1","quantity":2}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/orders/" + ORDER_ID))
                .andExpect(header().string("X-Trace-Id", not(blankOrNullString())))
                .andExpect(jsonPath("$.id").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].productId").value("SKU-1"));

        ArgumentCaptor<CreateOrderData> command = ArgumentCaptor.forClass(CreateOrderData.class);
        verify(service).create(command.capture());
        org.assertj.core.api.Assertions.assertThat(command.getValue().items().getFirst().quantity())
                .isEqualTo(2);
    }

    @Test
    void createRejectsValidationAndUnknownMembers() throws Exception {
        assertProblem(
                mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}")),
                400,
                "INVALID_REQUEST")
                .andExpect(jsonPath("$.violations[0].field").value("items"));

        assertProblem(
                mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"SKU\",\"quantity\":1}],\"status\":\"SHIPPED\"}")),
                400,
                "INVALID_REQUEST");

        assertProblem(
                mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"SKU\",\"quantity\":\"1\"}]}")),
                400,
                "INVALID_REQUEST");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.0", "1e0"})
    void createRejectsNonIntegerNumericRepresentations(String quantityJson) throws Exception {
        when(service.create(any(CreateOrderData.class))).thenReturn(detail("PENDING"));

        assertProblem(
                mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":"SKU","quantity":%s}]}
                                """.formatted(quantityJson))),
                400,
                "INVALID_REQUEST");
    }

    @ParameterizedTest
    @MethodSource("duplicateJsonBodies")
    void createRejectsDuplicateJsonMembers(String requestBody) throws Exception {
        when(service.create(any(CreateOrderData.class))).thenReturn(detail("PENDING"));

        assertProblem(
                mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)),
                400,
                "INVALID_REQUEST");
    }

    @Test
    void createRejectsUnknownMembersNestedInsideAnItem() throws Exception {
        assertProblem(
                mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":"SKU","quantity":1,"position":0}]}
                                """)),
                400,
                "INVALID_REQUEST");
    }

    @Test
    void listUsesDefaultsAndMapsPageMetadata() throws Exception {
        when(service.list(null, 0, 20)).thenReturn(new OrderPage(
                List.of(new OrderPage.Summary(
                        ORDER_ID, "PENDING", 2, CREATED, CREATED)),
                0,
                20,
                1,
                1,
                true,
                true));

        mockMvc.perform(get("/api/v1/orders").queryParam("sort", "id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].itemCount").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.last").value(true));
        verify(service).list(null, 0, 20);
    }

    @Test
    void listRejectsRepeatedAndMalformedDocumentedParameters() throws Exception {
        assertProblem(
                mockMvc.perform(get("/api/v1/orders")
                        .queryParam("status", "PENDING", "SHIPPED")),
                400,
                "INVALID_REQUEST")
                .andExpect(jsonPath("$.violations[0].field").value("status"));

        assertProblem(
                mockMvc.perform(get("/api/v1/orders").queryParam("page", "one")),
                400,
                "INVALID_REQUEST")
                .andExpect(jsonPath("$.violations[0].field").value("page"));
    }

    @Test
    void listMapsAFilteredPageAndAValidEmptyPage() throws Exception {
        when(service.list("CANCELLED", 1, 5)).thenReturn(new OrderPage(
                List.of(new OrderPage.Summary(
                        ORDER_ID, "CANCELLED", 1, CREATED, CREATED)),
                1,
                5,
                6,
                2,
                false,
                true));
        when(service.list("DELIVERED", 4, 10)).thenReturn(new OrderPage(
                List.of(),
                4,
                10,
                0,
                0,
                false,
                true));

        mockMvc.perform(get("/api/v1/orders")
                        .queryParam("status", "CANCELLED")
                        .queryParam("page", "1")
                        .queryParam("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.content[0].itemCount").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(6));

        mockMvc.perform(get("/api/v1/orders")
                        .queryParam("status", "DELIVERED")
                        .queryParam("page", "4")
                        .queryParam("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", empty()))
                .andExpect(jsonPath("$.page").value(4))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));

        verify(service).list("CANCELLED", 1, 5);
        verify(service).list("DELIVERED", 4, 10);
    }

    @Test
    void retrieveAndTransitionSuccessReturnFullUpdatedDetails() throws Exception {
        when(service.get(ORDER_ID)).thenReturn(detail("PROCESSING"));
        when(service.updateStatus(ORDER_ID, "SHIPPED")).thenReturn(detail("SHIPPED"));

        mockMvc.perform(get("/api/v1/orders/{orderId}", ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.createdAt").value(CREATED.toString()));

        mockMvc.perform(patch("/api/v1/orders/{orderId}/status", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.updatedAt").value(CREATED.toString()));

        verify(service).get(ORDER_ID);
        verify(service).updateStatus(ORDER_ID, "SHIPPED");
    }

    @Test
    void malformedUuidAndApplicationFailuresUseStableProblemShapes() throws Exception {
        assertProblem(
                mockMvc.perform(get("/api/v1/orders/not-a-uuid")),
                400,
                "INVALID_REQUEST")
                .andExpect(jsonPath("$.violations[0].field").value("orderId"));

        when(service.get(ORDER_ID)).thenThrow(new OrderNotFoundException(ORDER_ID));
        assertProblem(
                mockMvc.perform(get("/api/v1/orders/{orderId}", ORDER_ID)),
                404,
                "ORDER_NOT_FOUND");

        when(service.updateStatus(ORDER_ID, "SHIPPED"))
                .thenThrow(new OrderStateConflictException(ORDER_ID));
        assertProblem(
                mockMvc.perform(patch("/api/v1/orders/{orderId}/status", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPED\"}")),
                409,
                "ORDER_STATE_CONFLICT");
    }

    @Test
    void cancelAcceptsOnlyAZeroByteBody() throws Exception {
        when(service.cancel(ORDER_ID)).thenReturn(detail("CANCELLED"));

        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", ORDER_ID)
                        .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertProblem(
                mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(" ")),
                400,
                "INVALID_REQUEST")
                .andExpect(jsonPath("$.violations[0].field").value("$"));

        assertProblem(
                mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null")),
                400,
                "INVALID_REQUEST");

        assertProblem(
                mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")),
                400,
                "INVALID_REQUEST");
    }

    @Test
    void frameworkHttpFailuresUseProblemDetailsFor405406And415() throws Exception {
        assertProblem(
                mockMvc.perform(delete("/api/v1/orders/{orderId}", ORDER_ID)),
                405,
                "METHOD_NOT_ALLOWED")
                .andExpect(header().string("Allow", not(blankOrNullString())));

        assertProblem(
                mockMvc.perform(get("/api/v1/orders/{orderId}", ORDER_ID)
                        .accept(MediaType.APPLICATION_XML)),
                406,
                "NOT_ACCEPTABLE");

        assertProblem(
                mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}")),
                415,
                "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void unexpectedFailureIsSanitizedAs500() throws Exception {
        when(service.get(ORDER_ID)).thenThrow(new IllegalStateException("sensitive details"));

        assertProblem(
                mockMvc.perform(get("/api/v1/orders/{orderId}", ORDER_ID)),
                500,
                "INTERNAL_ERROR")
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("sensitive details"))));
    }

    @Test
    void serviceValidationViolationsAreSortedByFieldThenMessage() throws Exception {
        when(service.list(null, 0, 20)).thenThrow(new InvalidOrderException(List.of(
                new InvalidOrderException.Violation("size", "z"),
                new InvalidOrderException.Violation("page", "b"),
                new InvalidOrderException.Violation("page", "a"))));

        assertProblem(mockMvc.perform(get("/api/v1/orders")), 400, "INVALID_REQUEST")
                .andExpect(jsonPath("$.violations[0].field").value("page"))
                .andExpect(jsonPath("$.violations[0].message").value("a"))
                .andExpect(jsonPath("$.violations[1].message").value("b"))
                .andExpect(jsonPath("$.violations[2].field").value("size"));
    }

    @Test
    void problemHasExactlyEightMembersAndUsesAServerGeneratedMatchingTraceId() throws Exception {
        MvcResult result = assertProblem(
                mockMvc.perform(get("/api/v1/orders/not-a-uuid")
                        .header("X-Trace-Id", "caller-controlled-trace")),
                400,
                "INVALID_REQUEST")
                .andExpect(jsonPath("$", aMapWithSize(8)))
                .andReturn();

        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        String bodyTraceId = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.traceId");
        assertThat(headerTraceId)
                .isEqualTo(bodyTraceId)
                .isNotEqualTo("caller-controlled-trace");
    }

    @Test
    void unmappedResourceUsesStable404ProblemDetails() throws Exception {
        assertProblem(
                mockMvc.perform(get("/api/v1/not-a-resource")),
                404,
                "RESOURCE_NOT_FOUND")
                .andExpect(jsonPath("$.instance").value("/api/v1/not-a-resource"));
    }

    private static ResultActions assertProblem(ResultActions actions, int httpStatus, String code)
            throws Exception {
        return actions
                .andExpect(status().is(httpStatus))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").exists())
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.status").value(httpStatus))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.instance").exists())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(header().string("X-Trace-Id", not(blankOrNullString())))
                .andExpect(jsonPath("$.traceId", not(blankOrNullString())))
                .andExpect(jsonPath("$.violations").exists())
                .andExpect(jsonPath("$", aMapWithSize(8)));
    }

    private static OrderDetails detail(String status) {
        return new OrderDetails(
                ORDER_ID,
                status,
                List.of(new OrderDetails.Item("SKU-1", 2)),
                CREATED,
                CREATED);
    }

    private static Stream<String> duplicateJsonBodies() {
        return Stream.of(
                """
                        {"items":[{"productId":"SKU-1","quantity":1}],
                         "items":[{"productId":"SKU-2","quantity":2}]}
                        """,
                """
                        {"items":[{"productId":"SKU","quantity":1,"quantity":2}]}
                        """);
    }
}
