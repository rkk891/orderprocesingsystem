package com.rkk.orderprocessing.order.persistence;

import com.rkk.orderprocessing.order.domain.OrderStatus;
import com.rkk.orderprocessing.testsupport.PostgresTestConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies order mappings and atomic repository statements against PostgreSQL.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
@Transactional
class OrderRepositoryIT {

    @Autowired
    private OrderRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistsAndFetchesACompleteAggregateInPositionOrder() {
        OrderEntity saved = repository.saveAndFlush(order(
                "352d67de-fdfd-438f-929a-3ae4f5191063",
                "2026-07-13T08:00:00Z",
                OrderItemEntity.create(1, "SKU-B", 2),
                OrderItemEntity.create(0, "SKU-A", 1)
        ));
        entityManager.clear();

        OrderEntity detail = repository.findDetailById(saved.getId()).orElseThrow();

        assertThat(detail.getItems())
                .extracting(OrderItemEntity::getPosition)
                .containsExactly(0, 1);
        assertThat(detail.getItems())
                .extracting(OrderItemEntity::getProductId)
                .containsExactly("SKU-A", "SKU-B");
    }

    @Test
    void returnsStableSummaryPagesWithItemCountsAndExactStatusFiltering() {
        OrderEntity older = repository.save(order(
                "0724cc43-684d-4bbf-be0c-5b837e7f0844",
                "2026-07-13T08:00:00Z",
                OrderItemEntity.create(0, "SKU-OLD", 1)
        ));
        OrderEntity newer = repository.save(order(
                "b0993b9a-7508-4c71-8e67-100279b064ed",
                "2026-07-13T09:00:00Z",
                OrderItemEntity.create(0, "SKU-NEW-1", 1),
                OrderItemEntity.create(1, "SKU-NEW-2", 1)
        ));
        repository.flush();

        assertThat(repository.updateStatusIfExpected(
                older.getId(),
                OrderStatus.PENDING,
                OrderStatus.CANCELLED,
                Instant.parse("2026-07-13T10:00:00Z")
        )).isOne();

        Page<OrderRepository.OrderSummaryProjection> firstPage =
                repository.findSummaryPage(PageRequest.of(0, 1));
        Page<OrderRepository.OrderSummaryProjection> cancelledPage =
                repository.findSummaryPageByStatus(OrderStatus.CANCELLED, PageRequest.of(0, 10));

        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getContent()).singleElement().satisfies(summary -> {
            assertThat(summary.getId()).isEqualTo(newer.getId());
            assertThat(summary.getItemCount()).isEqualTo(2);
        });
        assertThat(cancelledPage.getTotalElements()).isOne();
        assertThat(cancelledPage.getContent()).singleElement().satisfies(summary -> {
            assertThat(summary.getId()).isEqualTo(older.getId());
            assertThat(summary.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(summary.getItemCount()).isOne();
        });
    }

    @Test
    void ordersEqualCreationTimesByUuidDescending() {
        String timestamp = "2026-07-13T09:00:00Z";
        UUID lowerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID higherId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        repository.save(order(
                lowerId.toString(), timestamp, OrderItemEntity.create(0, "SKU-LOW", 1)));
        repository.save(order(
                higherId.toString(), timestamp, OrderItemEntity.create(0, "SKU-HIGH", 1)));
        repository.flush();

        Page<OrderRepository.OrderSummaryProjection> page =
                repository.findSummaryPage(PageRequest.of(0, 10));

        assertThat(page.getContent())
                .extracting(OrderRepository.OrderSummaryProjection::getId)
                .containsExactly(higherId, lowerId);
    }

    @Test
    void detailAndSummaryReadsUseBoundedStatementCounts() {
        OrderEntity first = repository.save(order(
                "0b5c9ac8-c65d-45e5-acd7-06f840bb21b1",
                "2026-07-13T09:00:00Z",
                OrderItemEntity.create(0, "QUERY-COUNT-1", 1),
                OrderItemEntity.create(1, "QUERY-COUNT-2", 1)));
        repository.save(order(
                "65e89b89-78e4-494c-885e-5cc395b104bf",
                "2026-07-13T08:00:00Z",
                OrderItemEntity.create(0, "QUERY-COUNT-3", 1)));
        repository.flush();
        entityManager.clear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        OrderEntity detail = repository.findDetailById(first.getId()).orElseThrow();
        assertThat(detail.getItems()).hasSize(2);
        assertThat(statistics.getPrepareStatementCount()).isOne();

        statistics.clear();
        Page<OrderRepository.OrderSummaryProjection> page =
                repository.findSummaryPage(PageRequest.of(0, 1));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void listingQueriesCanUseTheDesignedIndexes() {
        repository.saveAndFlush(order(
                "2647886a-ac1e-4c80-a7d1-46f307061d21",
                "2026-07-13T09:00:00Z",
                OrderItemEntity.create(0, "INDEX-PROOF", 1)));
        entityManager.clear();
        jdbcTemplate.execute("set local enable_seqscan = off");

        String unfilteredPlan = String.join("\n", jdbcTemplate.queryForList(
                "explain (costs off) select id, status, created_at, updated_at "
                        + "from orders order by created_at desc, id desc limit 20",
                String.class));
        String filteredPlan = String.join("\n", jdbcTemplate.queryForList(
                "explain (costs off) select id, status, created_at, updated_at "
                        + "from orders where status = 'PENDING' "
                        + "order by created_at desc, id desc limit 20",
                String.class));

        assertThat(unfilteredPlan).contains("idx_orders_created_id");
        assertThat(filteredPlan).contains("idx_orders_status_created_id");
    }

    @Test
    void appliesExpectedStatusMutationAndKeepsUpdatedAtMonotonic() {
        OrderEntity order = repository.saveAndFlush(order(
                "6e7c6e1c-7372-457a-b3fa-f69a7484fe99",
                "2026-07-13T08:00:00Z",
                OrderItemEntity.create(0, "SKU-001", 1)
        ));

        int updated = repository.updateStatusIfExpected(
                order.getId(),
                OrderStatus.PENDING,
                OrderStatus.PROCESSING,
                Instant.parse("2026-07-13T07:00:00Z")
        );
        int stale = repository.updateStatusIfExpected(
                order.getId(),
                OrderStatus.PENDING,
                OrderStatus.CANCELLED,
                Instant.parse("2026-07-13T09:00:00Z")
        );

        OrderEntity detail = repository.findDetailById(order.getId()).orElseThrow();
        assertThat(updated).isOne();
        assertThat(stale).isZero();
        assertThat(detail.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(detail.getUpdatedAt()).isEqualTo(Instant.parse("2026-07-13T08:00:00Z"));
    }

    @Test
    void promotesOnlyPendingRowsAndIsIdempotent() {
        OrderEntity first = repository.save(order(
                "491217fc-bfe3-40e7-890d-a20ee72ef927",
                "2026-07-13T08:00:00Z",
                OrderItemEntity.create(0, "SKU-1", 1)
        ));
        OrderEntity second = repository.save(order(
                "981f83dd-c52c-4403-a423-6515caa3eb49",
                "2026-07-13T08:01:00Z",
                OrderItemEntity.create(0, "SKU-2", 1)
        ));
        OrderEntity cancelled = repository.save(order(
                "c3df30ae-167b-4fa2-93be-4b8f0af48499",
                "2026-07-13T08:02:00Z",
                OrderItemEntity.create(0, "SKU-3", 1)
        ));
        repository.flush();
        repository.updateStatusIfExpected(
                cancelled.getId(),
                OrderStatus.PENDING,
                OrderStatus.CANCELLED,
                Instant.parse("2026-07-13T08:03:00Z")
        );

        assertThat(repository.processPending(Instant.parse("2026-07-13T08:05:00Z"))).isEqualTo(2);
        assertThat(repository.processPending(Instant.parse("2026-07-13T08:10:00Z"))).isZero();
        assertThat(repository.findDetailById(first.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PROCESSING);
        assertThat(repository.findDetailById(second.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PROCESSING);
        assertThat(repository.findDetailById(cancelled.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void databaseRejectsDuplicateProductsWithinAnOrder() {
        OrderEntity order = order(
                "6ba418fb-8503-4916-8e82-24906ad9cefd",
                "2026-07-13T08:00:00Z",
                OrderItemEntity.create(0, "DUPLICATE", 1),
                OrderItemEntity.create(1, "DUPLICATE", 2)
        );

        assertThatThrownBy(() -> repository.saveAndFlush(order))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsUnknownStatus() {
        UUID invalidStatus = UUID.randomUUID();
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into orders (id, status, created_at, updated_at) values (?, ?, ?, ?)",
                invalidStatus,
                "UNKNOWN",
                at("2026-07-13T08:00:00Z"),
                at("2026-07-13T08:00:00Z")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsBackwardTimestamps() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into orders (id, status, created_at, updated_at) values (?, 'PENDING', ?, ?)",
                UUID.randomUUID(),
                at("2026-07-13T09:00:00Z"),
                at("2026-07-13T08:00:00Z")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidProductIds")
    void databaseRejectsInvalidProductIdentifiers(String productId) {
        UUID orderId = insertPendingOrder();
        assertThatThrownBy(() -> insertItem(orderId, 0, productId, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidQuantities")
    void databaseRejectsOutOfRangeQuantities(int quantity) {
        UUID orderId = insertPendingOrder();
        assertThatThrownBy(() -> insertItem(orderId, 0, "SKU", quantity))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidPositions")
    void databaseRejectsOutOfRangePositions(int position) {
        UUID orderId = insertPendingOrder();
        assertThatThrownBy(() -> insertItem(orderId, position, "SKU", 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsMissingParentsAndDuplicatePositions() {
        assertThatThrownBy(() -> insertItem(UUID.randomUUID(), 0, "ORPHAN", 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsDuplicatePositions() {
        UUID orderId = insertPendingOrder();
        insertItem(orderId, 0, "SKU-1", 1);
        assertThatThrownBy(() -> insertItem(orderId, 0, "SKU-2", 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static OrderEntity order(String id, String timestamp, OrderItemEntity... items) {
        return OrderEntity.createPending(
                UUID.fromString(id),
                Instant.parse(timestamp),
                List.of(items)
        );
    }

    private UUID insertPendingOrder() {
        UUID orderId = UUID.randomUUID();
        OffsetDateTime timestamp = at("2026-07-13T08:00:00Z");
        jdbcTemplate.update(
                "insert into orders (id, status, created_at, updated_at) values (?, 'PENDING', ?, ?)",
                orderId,
                timestamp,
                timestamp);
        return orderId;
    }

    private void insertItem(UUID orderId, int position, String productId, int quantity) {
        jdbcTemplate.update(
                "insert into order_items (order_id, position, product_id, quantity) values (?, ?, ?, ?)",
                orderId,
                position,
                productId,
                quantity);
    }

    private static OffsetDateTime at(String instant) {
        return Instant.parse(instant).atOffset(ZoneOffset.UTC);
    }

    private static List<String> invalidProductIds() {
        return List.of("   ", "x".repeat(101));
    }

    private static List<Integer> invalidQuantities() {
        return List.of(0, 1000);
    }

    private static List<Integer> invalidPositions() {
        return List.of(-1, 100);
    }
}
