package com.rkk.orderprocessing.order.application;

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
 * Transactional facade for all synchronous order use cases.
 *
 * <p>All operation state stays in local variables so the Spring singleton remains safe across
 * concurrent requests. Lifecycle writes delegate correctness to conditional database updates.</p>
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
    private final OrderResultMapper resultMapper;
    private final Clock clock;

    public OrderService(OrderRepository repository, OrderResultMapper resultMapper, Clock clock) {
        this.repository = repository;
        this.resultMapper = resultMapper;
        this.clock = clock;
    }

    /** Validates and persists one pending aggregate atomically. */
    @Transactional
    public OrderDetailsResult create(CreateOrderCommand command) {
        validateCreate(command);

        List<OrderItemEntity> itemEntities = new ArrayList<>(command.items().size());
        for (int position = 0; position < command.items().size(); position++) {
            CreateOrderCommand.Item item = command.items().get(position);
            itemEntities.add(OrderItemEntity.create(position, item.productId(), item.quantity()));
        }

        Instant timestamp = clock.instant();
        OrderEntity order = OrderEntity.createPending(UUID.randomUUID(), timestamp, itemEntities);
        return resultMapper.toDetails(repository.save(order));
    }

    /** Retrieves and detaches one complete aggregate. */
    @Transactional(readOnly = true)
    public OrderDetailsResult get(UUID orderId) {
        requireOrderId(orderId);
        return repository.findDetailById(orderId)
                .map(resultMapper::toDetails)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    /** Lists a bounded page, optionally filtered by one exact status name. */
    @Transactional(readOnly = true)
    public OrderPageResult list(String status, int page, int size) {
        validatePage(page, size);
        PageRequest pageable = PageRequest.of(page, size);

        if (status == null) {
            return resultMapper.toPage(repository.findSummaryPage(pageable));
        }

        OrderStatus parsedStatus = parseStatus(status);
        return resultMapper.toPage(repository.findSummaryPageByStatus(parsedStatus, pageable));
    }

    /** Advances to exactly the next fulfilment status using an atomic expected-state predicate. */
    @Transactional
    public OrderDetailsResult advanceStatus(UUID orderId, String requestedStatus) {
        requireOrderId(orderId);
        OrderStatus target = parseStatus(requestedStatus);
        OrderStatus predecessor = target.requiredPredecessorForManualTarget()
                .orElseThrow(() -> new OrderStateConflictException(orderId));
        return transition(orderId, predecessor, target);
    }

    /** Cancels an order only while its committed state remains pending. */
    @Transactional
    public OrderDetailsResult cancel(UUID orderId) {
        requireOrderId(orderId);
        return transition(orderId, OrderStatus.PENDING, OrderStatus.CANCELLED);
    }

    private OrderDetailsResult transition(
            UUID orderId,
            OrderStatus expectedStatus,
            OrderStatus targetStatus) {
        int affected = repository.updateStatusIfExpected(
                orderId, expectedStatus, targetStatus, clock.instant());

        if (affected == 1) {
            return repository.findDetailById(orderId)
                    .map(resultMapper::toDetails)
                    .orElseThrow(() -> new IllegalStateException(
                            "Updated order disappeared before response mapping"));
        }
        if (affected != 0) {
            throw new IllegalStateException("Conditional order mutation affected multiple rows");
        }
        if (!repository.existsById(orderId)) {
            throw new OrderNotFoundException(orderId);
        }
        throw new OrderStateConflictException(orderId);
    }

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

    private static void validateCreate(CreateOrderCommand command) {
        List<InvalidOrderException.Violation> violations = new ArrayList<>();
        if (command == null) {
            throw InvalidOrderException.at("$", "must be provided");
        }

        List<CreateOrderCommand.Item> items = command.items();
        if (items == null) {
            throw InvalidOrderException.at("items", "must be provided");
        }
        if (items.size() < MIN_ITEMS || items.size() > MAX_ITEMS) {
            violations.add(new InvalidOrderException.Violation(
                    "items", "must contain between 1 and 100 items"));
        }

        Set<String> productIds = new HashSet<>();
        for (int index = 0; index < items.size(); index++) {
            CreateOrderCommand.Item item = items.get(index);
            String itemPath = "items[" + index + "]";
            if (item == null) {
                violations.add(new InvalidOrderException.Violation(
                        itemPath, "must not be null"));
                continue;
            }

            String productId = item.productId();
            if (productId == null) {
                violations.add(new InvalidOrderException.Violation(
                        itemPath + ".productId", "must be provided"));
            } else {
                validateProductId(productId, itemPath + ".productId", violations);
                if (!productIds.add(productId)) {
                    violations.add(new InvalidOrderException.Violation(
                            itemPath + ".productId", "must be unique within the order"));
                }
            }

            if (item.quantity() < MIN_QUANTITY || item.quantity() > MAX_QUANTITY) {
                violations.add(new InvalidOrderException.Violation(
                        itemPath + ".quantity", "must be between 1 and 999"));
            }
        }

        if (!violations.isEmpty()) {
            throw new InvalidOrderException(violations);
        }
    }

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

    private static void requireOrderId(UUID orderId) {
        if (orderId == null) {
            throw InvalidOrderException.at("orderId", "must be a valid UUID");
        }
    }
}
