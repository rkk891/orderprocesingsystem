package com.rkk.orderprocessing.order.api;

import com.rkk.orderprocessing.order.api.request.CreateOrderRequest;
import com.rkk.orderprocessing.order.api.request.UpdateOrderStatusRequest;
import com.rkk.orderprocessing.order.api.response.OrderPageResponse;
import com.rkk.orderprocessing.order.api.response.OrderResponse;
import com.rkk.orderprocessing.order.application.exception.InvalidOrderException;
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

/**
 * Versioned HTTP adapter for synchronous order use cases.
 * This controller handles external API requests, maps them to application commands, invokes the
 * application service, and maps results back to HTTP responses.
 */
@RestController
@RequestMapping(path = "/api/v1/orders", produces = MediaType.APPLICATION_JSON_VALUE)
public class OrderController {

    // Only documented parameters are checked for duplicates. Unknown parameters are ignored as
    // required by the API contract.
    private static final Set<String> LIST_PARAMETERS = Set.of("status", "page", "size");
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final OrderService service;
    private final OrderApiMapper mapper;

    /**
     * Creates the HTTP controller with the service and API mapper it delegates to.
     *
     * @param service runs order operations and applies business rules
     * @param mapper converts between HTTP objects and application objects
     */
    public OrderController(OrderService service, OrderApiMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /**
     * Creates one independent order.
     *
     * @param request the HTTP request payload containing the order details to create.
     * @return a ResponseEntity containing the created order and a Location header.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        // Map the HTTP request into an application command. The service applies the business rules,
        // and the mapper converts its result back into the public API response shape.
        OrderResponse response = mapper.toResponse(service.create(mapper.toCommand(request)));

        // Return 201 Created with the relative location URI for the newly created resource.
        return ResponseEntity.created(URI.create("/api/v1/orders/" + response.id()))
                .body(response);
    }

    /**
     * Returns complete details for one valid order UUID.
     *
     * @param orderId the unique identifier of the requested order.
     * @return the HTTP response payload containing the complete order detail.
     */
    @GetMapping("/{orderId}")
    public OrderResponse get(@PathVariable UUID orderId) {
        // Mapping here keeps database entity classes out of the public API response.
        return mapper.toResponse(service.get(orderId));
    }

    /**
     * Returns one page of order summaries. Undocumented parameters are ignored, while repeated
     * documented parameters are rejected.
     * Scalar {@code @RequestParam} binding is intentionally avoided because it silently selects
     * one value when a parameter is repeated.
     *
     * @param parameters a map of all incoming query parameters.
     * @return a paginated response containing a list of order summaries.
     */
    @GetMapping
    public OrderPageResponse list(@RequestParam MultiValueMap<String, String> parameters) {
        // Read the multi-value map directly so repeated parameters can be rejected reliably.
        PageQuery query = parsePageQuery(parameters);

        // The service returns application data; the mapper gives it the public response shape.
        return mapper.toResponse(service.list(query.status(), query.page(), query.size()));
    }

    /**
     * Moves an order forward by exactly one allowed fulfilment step, such as
     * {@code PROCESSING -> SHIPPED}.
     *
     * @param orderId the unique identifier of the order to transition.
     * @param request the HTTP request payload specifying the desired target status.
     * @return the updated order details after the state transition.
     */
    @PatchMapping(path = "/{orderId}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public OrderResponse advanceStatus(
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        // The service checks the allowed previous status and performs the guarded database update.
        return mapper.toResponse(service.advanceStatus(orderId, request.status()));
    }

    /**
     * Cancels a pending order. Reading raw bytes distinguishes a genuinely absent body from JSON
     * {@code null}, an empty object, or whitespace, all of which the contract rejects.
     *
     * @param orderId the unique identifier of the order to cancel.
     * @param body the raw request bytes; {@code null} or an empty array means no body was sent.
     * @return the updated order details after cancellation.
     * @throws InvalidOrderException if the request contains any body bytes.
     */
    @PostMapping("/{orderId}/cancel")
    public OrderResponse cancel(
            @PathVariable UUID orderId,
            @RequestBody(required = false) byte[] body) {
        // Any bytes, including JSON null, an object, or whitespace, violate the cancel contract.
        if (body != null && body.length > 0) {
            throw InvalidOrderException.at("$", "request body must be absent");
        }

        // The service permits cancellation only while the saved status is PENDING.
        return mapper.toResponse(service.cancel(orderId));
    }

    /**
     * Parses the HTTP query parameters into a validated PageQuery object.
     *
     * @param parameters the raw multi-value map from the request query string.
     * @return a parsed PageQuery containing status, page, and size.
     * @throws InvalidOrderException if parameter constraints are violated.
     */
    private static PageQuery parsePageQuery(MultiValueMap<String, String> parameters) {
        List<InvalidOrderException.Violation> violations = new ArrayList<>();

        // Collect all repeated documented parameters so the client receives every error at once.
        parameters.forEach((name, values) -> {
            if (LIST_PARAMETERS.contains(name) && (values == null || values.size() != 1)) {
                violations.add(new InvalidOrderException.Violation(
                        name, "must be specified at most once"));
            }
        });

        if (!violations.isEmpty()) {
            throw new InvalidOrderException(violations);
        }

        // Missing page and size values use the documented defaults.
        String status = singleValue(parameters, "status");
        int page = parseInteger(singleValue(parameters, "page"), "page", DEFAULT_PAGE);
        int size = parseInteger(singleValue(parameters, "size"), "size", DEFAULT_SIZE);

        return new PageQuery(status, page, size);
    }

    /**
     * Extracts a single string value from the multi-value map.
     *
     * @param parameters the map containing query values.
     * @param name the key to extract.
     * @return the first value in the list if present, or null otherwise.
     */
    private static String singleValue(MultiValueMap<String, String> parameters, String name) {
        List<String> values = parameters.get(name);
        return values == null ? null : values.getFirst();
    }

    /**
     * Parses a string query parameter into an integer with fallback to a default.
     *
     * @param value the string value to parse.
     * @param field the name of the field for error reporting.
     * @param defaultValue the fallback value if the parameter is absent.
     * @return the parsed integer value.
     * @throws InvalidOrderException if the string cannot be parsed as a valid integer.
     */
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

    /**
     * Validated values used to request one page of orders.
     *
     * @param status the requested status filter, or {@code null} for all statuses
     * @param page the zero-based page number
     * @param size the maximum number of summaries on the page
     */
    private record PageQuery(String status, int page, int size) {
    }
}
