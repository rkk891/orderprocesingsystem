package com.rkk.orderprocessing.order.api;

import com.rkk.orderprocessing.order.api.dto.CreateOrderRequest;
import com.rkk.orderprocessing.order.api.dto.OrderPageResponse;
import com.rkk.orderprocessing.order.api.dto.OrderResponse;
import com.rkk.orderprocessing.order.api.dto.UpdateOrderStatusRequest;
import com.rkk.orderprocessing.order.application.InvalidOrderException;
import com.rkk.orderprocessing.order.application.OrderService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Versioned HTTP adapter for synchronous order use cases. */
@RestController
@RequestMapping(path = "/api/v1/orders", produces = MediaType.APPLICATION_JSON_VALUE)
public class OrderController {

    private static final Set<String> LIST_PARAMETERS = Set.of("status", "page", "size");
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final OrderService service;
    private final OrderApiMapper mapper;

    public OrderController(OrderService service, OrderApiMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** Creates one independent order and returns its stable relative resource location. */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = mapper.toResponse(service.create(mapper.toCommand(request)));
        return ResponseEntity.created(URI.create("/api/v1/orders/" + response.id()))
                .body(response);
    }

    /** Returns complete detail for one syntactically valid UUID. */
    @GetMapping("/{orderId}")
    public OrderResponse get(@PathVariable UUID orderId) {
        return mapper.toResponse(service.get(orderId));
    }

    /**
     * Lists bounded summaries while ignoring undocumented parameters and rejecting repeated
     * documented parameters.
     * Scalar {@code @RequestParam} binding is intentionally avoided because it silently selects
     * one value when a parameter is repeated.
     */
    @GetMapping
    public OrderPageResponse list(@RequestParam MultiValueMap<String, String> parameters) {
        PageQuery query = parsePageQuery(parameters);
        return mapper.toResponse(service.list(query.status(), query.page(), query.size()));
    }

    /** Requests one adjacent fulfilment transition. */
    @PatchMapping(path = "/{orderId}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public OrderResponse advanceStatus(
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        return mapper.toResponse(service.advanceStatus(orderId, request.status()));
    }

    /**
     * Cancels a pending order. Reading raw bytes distinguishes a genuinely absent body from JSON
     * {@code null}, an empty object, or whitespace, all of which the contract rejects.
     */
    @PostMapping("/{orderId}/cancel")
    public OrderResponse cancel(
            @PathVariable UUID orderId,
            @RequestBody(required = false) byte[] body) {
        if (body != null && body.length > 0) {
            throw InvalidOrderException.at("$", "request body must be absent");
        }
        return mapper.toResponse(service.cancel(orderId));
    }

    private static PageQuery parsePageQuery(MultiValueMap<String, String> parameters) {
        List<InvalidOrderException.Violation> violations = new ArrayList<>();

        parameters.forEach((name, values) -> {
            if (LIST_PARAMETERS.contains(name) && (values == null || values.size() != 1)) {
                violations.add(new InvalidOrderException.Violation(
                        name, "must be specified at most once"));
            }
        });

        if (!violations.isEmpty()) {
            throw new InvalidOrderException(violations);
        }

        String status = singleValue(parameters, "status");
        int page = parseInteger(singleValue(parameters, "page"), "page", DEFAULT_PAGE);
        int size = parseInteger(singleValue(parameters, "size"), "size", DEFAULT_SIZE);
        return new PageQuery(status, page, size);
    }

    private static String singleValue(MultiValueMap<String, String> parameters, String name) {
        List<String> values = parameters.get(name);
        return values == null ? null : values.getFirst();
    }

    private static int parseInteger(String value, String field, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw InvalidOrderException.at(field, "must be an integer");
        }
    }

    private record PageQuery(String status, int page, int size) {}
}
