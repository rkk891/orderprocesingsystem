package com.rkk.orderprocessing.order.application;

import com.rkk.orderprocessing.order.application.command.CreateOrderData;
import com.rkk.orderprocessing.order.application.exception.InvalidOrderException;
import com.rkk.orderprocessing.order.application.exception.OrderNotFoundException;
import com.rkk.orderprocessing.order.application.exception.OrderStateConflictException;
import com.rkk.orderprocessing.order.application.result.OrderDetails;
import com.rkk.orderprocessing.order.application.result.OrderPage;
import com.rkk.orderprocessing.order.domain.OrderStatus;
import com.rkk.orderprocessing.order.persistence.OrderEntity;
import com.rkk.orderprocessing.order.persistence.OrderItemEntity;
import com.rkk.orderprocessing.order.persistence.OrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the order use cases and defines their transaction boundaries.
 * Orchestrates business rules, validation, and database updates for orders.
 *
 * <p>Spring creates one shared {@code OrderService} object. Each request keeps its working data in
 * method-local variables, so concurrent requests do not overwrite one another. Status changes use
 * a database update that checks the current status, so only one competing request can succeed.</p>
 *
 * <p><b>Annotation Mechanics:</b>
 * <ul>
 *   <li>{@code @Transactional}: Spring creates a proxy around this class to manage transaction boundaries.
 *   It automatically opens a transaction when a method starts, commits it on success, and rolls it
 *   back if an unchecked exception occurs.</li>
 *   <li>{@code @Transactional(readOnly = true)}: Used on read methods (like {@code get} and {@code list})
 *   to optimize performance by skipping Hibernate's dirty-checking and allowing the database engine
 *   to optimize read locks.</li>
 * </ul>
 */
@Service
public class OrderService {

    private static final int MIN_ITEMS = 1;
    private static final int MAX_ITEMS = 100;
    private static final int MAX_PRODUCT_ID_CODE_POINTS = 100;
    private static final int MIN_QUANTITY = 1;
    private static final int MAX_QUANTITY = 999;
    private static final int MAX_PAGE_SIZE = 100;

    private final OrderRepository repository;
    private final DataMapper resultMapper;
    private final Clock clock;

    /**
     * Creates the service with the collaborators shared by every order operation.
     *
     * @param repository reads and writes saved orders
     * @param resultMapper copies saved data into application results
     * @param clock supplies timestamps and can be replaced with a fixed clock in tests
     */
    public OrderService(OrderRepository repository, DataMapper resultMapper, Clock clock) {
        this.repository = repository;
        this.resultMapper = resultMapper;
        this.clock = clock;
    }

    /**
     * Creates a new order with status {@code PENDING} and saves the order and all of its items in
     * one transaction. If any insert fails, Spring rolls back the complete order.
     *
     * @param orderData the products and quantities requested for the new order
     * @return the complete saved order
     * @throws InvalidOrderException if the orderData or any item is invalid
     */
    @Transactional
    public OrderDetails create(CreateOrderData orderData) {
        // 1. Validate all business rules and constraints before allocating any entities
        validateCreate(orderData);

        // 2. Map payload items to database entities
        List<OrderItemEntity> itemEntities = new ArrayList<>(orderData.items().size());
        for (int position = 0; position < orderData.items().size(); position++) {
            CreateOrderData.Item item = orderData.items().get(position);
            // We explicitly store the position index so that when we return the response,
            // the items are guaranteed to be in the exact same order the caller sent them.
            itemEntities.add(OrderItemEntity.create(position, item.productId(), item.quantity()));
        }

        // 3. Construct the root Order entity
        // We use a single clock instant so that createdAt and updatedAt match exactly on creation
        Instant timestamp = clock.instant();
        OrderEntity order = OrderEntity.createPending(UUID.randomUUID(), timestamp, itemEntities);

        // 4. Save to the database and map the resulting entity back to a domain object
        return resultMapper.toDetails(repository.save(order));
    }

    /**
     * Loads one order and all of its items inside a read-only transaction. The entity values are
     * copied into an application result before the transaction closes.
     *
     * @param orderId the ID of the order to load
     * @return the order and all of its items
     * @throws InvalidOrderException if {@code orderId} is null
     * @throws OrderNotFoundException if no order exists with that ID
     */
    @Transactional(readOnly = true)
    public OrderDetails get(UUID orderId) {
        requireOrderId(orderId);
        return repository.findDetailById(orderId)
                .map(resultMapper::toDetails)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    /**
     * Lists one page of order summaries. The summary query avoids loading every line item, and an
     * optional status value must exactly match a known status name.
     *
     * @param status the exact status name to include, or {@code null} to include all statuses
     * @param page the zero-based page number
     * @param size the number of order summaries requested per page
     * @return the matching summaries and page information
     * @throws InvalidOrderException if the status, page, or size is invalid
     */
    @Transactional(readOnly = true)
    public OrderPage list(String status, int page, int size) {
        validatePage(page, size);
        PageRequest pageable = PageRequest.of(page, size);

        if (status == null) {
            return resultMapper.toPage(repository.findSummaryPage(pageable));
        }

        OrderStatus parsedStatus = parseStatus(status);
        return resultMapper.toPage(repository.findSummaryPageByStatus(parsedStatus, pageable));
    }

    /**
     * Moves an order to the requested next fulfilment status. The database changes the row only
     * when its current status is the required previous status, preventing two concurrent requests
     * from both succeeding.
     *
     * @param orderId the ID of the order to update
     * @param requestedStatus the exact target status name
     * @return the order after the status change
     * @throws InvalidOrderException if the order ID or status is invalid
     * @throws OrderNotFoundException if the order does not exist
     * @throws OrderStateConflictException if the requested move is not allowed or another update wins first
     */
    @Transactional
    public OrderDetails updateStatus(UUID orderId, String requestedStatus) {
        requireOrderId(orderId);
        OrderStatus target = parseStatus(requestedStatus);
        OrderStatus predecessor = target.requiredPredecessorForManualTarget()
                .orElseThrow(() -> new OrderStateConflictException(orderId));
        return transition(orderId, predecessor, target);
    }

    /**
     * Cancels an order only while its saved status is {@code PENDING}. The same database status
     * check used by manual transitions prevents cancellation from overwriting another update.
     *
     * @param orderId the ID of the order to cancel
     * @return the cancelled order
     * @throws InvalidOrderException if {@code orderId} is null
     * @throws OrderNotFoundException if the order does not exist
     * @throws OrderStateConflictException if the order is no longer pending
     */
    @Transactional
    public OrderDetails cancel(UUID orderId) {
        requireOrderId(orderId);
        return transition(orderId, OrderStatus.PENDING, OrderStatus.CANCELLED);
    }

    /**
     * Runs one conditional status update. A row count of one means this request changed the order.
     * A row count of zero means either the order is missing or its status no longer matches, so an
     * existence check selects the correct error.
     *
     * @param orderId the ID of the order to update
     * @param expectedStatus the status that must currently be stored
     * @param targetStatus the new status to store
     * @return the order after the successful update
     * @throws OrderNotFoundException if no order exists with the ID
     * @throws OrderStateConflictException if the saved status does not match {@code expectedStatus}
     */
    private OrderDetails transition(
            UUID orderId,
            OrderStatus expectedStatus,
            OrderStatus targetStatus) {

        // 1. Attempt the atomic update using optimistic locking
        int affected = repository.updateStatusIfExpected(
                orderId, expectedStatus, targetStatus, clock.instant());

        // 2. Success Path: Exactly one row was updated
        if (affected == 1) {
            return repository.findDetailById(orderId)
                    .map(resultMapper::toDetails)
                    .orElseThrow(() -> new IllegalStateException(
                            "Updated order disappeared before response mapping"));
        }

        // 3. Safety Check: If more than 1 row was updated, we have a catastrophic data issue
        if (affected != 0) {
            throw new IllegalStateException("Conditional order mutation affected multiple rows");
        }

        // 4. Failure Path: Zero rows were updated.
        // This could mean one of two things: either the order doesn't exist, OR
        // someone else already updated it (state conflict). We check existence to find out which.
        if (!repository.existsById(orderId)) {
            throw new OrderNotFoundException(orderId);
        }

        // If it exists but wasn't updated, the 'expectedStatus' must have been wrong.
        throw new OrderStateConflictException(orderId);
    }

    /**
     * Converts an exact status name into {@link OrderStatus}.
     *
     * @param status the status text supplied by a caller
     * @return the matching status value
     * @throws InvalidOrderException if the value is null, blank, or unknown
     */
    private static OrderStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            throw InvalidOrderException.at("status", "must be a known order status");
        }
        try {
            return OrderStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw InvalidOrderException.at("status", "must be a known order status");
        }
    }

    /**
     * Checks the allowed page range and reports every pagination problem together.
     *
     * @param page the zero-based page number
     * @param size the requested number of summaries per page
     * @throws InvalidOrderException if page is negative or size is outside 1 to 100
     */
    private static void validatePage(int page, int size) {
        List<InvalidOrderException.Violation> violations = new ArrayList<>();

        if (page < 0) {
            violations.add(new InvalidOrderException.Violation("page", "must be at least 0"));
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            violations.add(new InvalidOrderException.Violation(
                    "size", "must be between 1 and 100"));
        }
        if (!violations.isEmpty()) {
            throw new InvalidOrderException(violations);
        }
    }

    /**
     * Checks the complete create data and collects item errors so callers receive all useful
     * constraints in one attempt.
     *
     * @param orderData the create-order input to check
     * @throws InvalidOrderException if the orderData, item count, product IDs, or quantities are invalid
     */
    private static void validateCreate(CreateOrderData orderData) {
        List<InvalidOrderException.Violation> violations = new ArrayList<>();

        // 1. Root Object Validation
        if (orderData == null) {
            throw InvalidOrderException.at("$", "must be provided");
        }

        List<CreateOrderData.Item> items = orderData.items();
        if (items == null) {
            throw InvalidOrderException.at("items", "must be provided");
        }

        // 2. Collection Size Validation
        if (items.size() < MIN_ITEMS || items.size() > MAX_ITEMS) {
            violations.add(new InvalidOrderException.Violation(
                    "items", "must contain between 1 and 100 items"));
        }

        Set<String> productIds = new HashSet<>();

        // 3. Line-Item Deep Validation
        // This explicit index loop is used instead of a standard for-each loop so we can
        // build accurate JSON-path error messages (e.g., "items[1].quantity") for the client.
        for (int index = 0; index < items.size(); index++) {
            CreateOrderData.Item item = items.get(index);
            String itemPath = "items[" + index + "]";

            if (item == null) {
                violations.add(new InvalidOrderException.Violation(
                        itemPath, "must not be null"));
                continue;
            }

            // 3a. Product ID Constraints
            String productId = item.productId();
            if (productId == null) {
                violations.add(new InvalidOrderException.Violation(
                        itemPath + ".productId", "must be provided"));
            } else {
                validateProductId(productId, itemPath + ".productId", violations);

                // HashSet.add returns false when this exact product ID was already submitted.
                // This guarantees we don't have duplicate items in the same order.
                if (!productIds.add(productId)) {
                    violations.add(new InvalidOrderException.Violation(
                            itemPath + ".productId", "must be unique within the order"));
                }
            }

            // 3b. Quantity Constraints
            if (item.quantity() < MIN_QUANTITY || item.quantity() > MAX_QUANTITY) {
                violations.add(new InvalidOrderException.Violation(
                        itemPath + ".quantity", "must be between 1 and 999"));
            }
        }

        // 4. Eject if any constraints failed
        // Throwing everything at once gives the client all errors in a single API response
        if (!violations.isEmpty()) {
            throw new InvalidOrderException(violations);
        }
    }

    /**
     * Checks product ID rules that cannot be expressed safely by a simple Java string length.
     * Unicode code points match the API length rule, and U+0000 is rejected because PostgreSQL
     * text values cannot store it.
     *
     * @param productId the product ID to check
     * @param field the field path used in validation errors
     * @param violations the list that collects validation errors
     */
    private static void validateProductId(
            String productId,
            String field,
            List<InvalidOrderException.Violation> violations) {
        if (productId.isBlank()) {
            violations.add(new InvalidOrderException.Violation(field, "must not be blank"));
        }

        int codePoints = productId.codePointCount(0, productId.length());
        if (codePoints < 1 || codePoints > MAX_PRODUCT_ID_CODE_POINTS) {
            violations.add(new InvalidOrderException.Violation(
                    field, "must contain between 1 and 100 Unicode code points"));
        }

        if (productId.indexOf('\u0000') >= 0) {
            violations.add(new InvalidOrderException.Violation(
                    field, "must not contain U+0000"));
        }
    }

    /**
     * Checks the order ID for non-HTTP callers. HTTP path IDs are already parsed by Spring, but the
     * service keeps the same validation rule for direct callers and tests.
     *
     * @param orderId the order ID to check
     * @throws InvalidOrderException if the ID is null
     */
    private static void requireOrderId(UUID orderId) {
        if (orderId == null) {
            throw InvalidOrderException.at("orderId", "must be a valid UUID");
        }
    }
}
