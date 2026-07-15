package com.rkk.orderprocessing.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rkk.orderprocessing.order.application.command.CreateOrderCommand;
import com.rkk.orderprocessing.order.application.exception.InvalidOrderException;
import com.rkk.orderprocessing.order.application.exception.OrderNotFoundException;
import com.rkk.orderprocessing.order.application.exception.OrderStateConflictException;
import com.rkk.orderprocessing.order.application.result.OrderDetailsResult;
import com.rkk.orderprocessing.order.application.result.OrderPageResult;
import com.rkk.orderprocessing.order.domain.OrderStatus;
import com.rkk.orderprocessing.order.persistence.OrderEntity;
import com.rkk.orderprocessing.order.persistence.OrderRepository;
import com.rkk.orderprocessing.order.persistence.OrderRepository.OrderSummaryProjection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/** Checks validation, use-case decisions, and missing-order versus state-conflict results. */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final UUID ORDER_ID = UUID.fromString("7bd36f1f-90d4-41dd-8a89-9aa622dfc0ad");
    private static final Instant NOW = Instant.parse("2026-07-11T12:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private OrderRepository repository;

    private final OrderResultMapper mapper = new OrderResultMapper();

    @Test
    void createBuildsOnePendingAggregateAndPreservesExactProductIds() {
        when(repository.save(any(OrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        OrderService service = service();

        OrderDetailsResult result = service.create(new CreateOrderCommand(List.of(
                new CreateOrderCommand.Item(" SKU ", 2),
                new CreateOrderCommand.Item("sku", 1))));

        ArgumentCaptor<OrderEntity> captor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(repository).save(captor.capture());
        OrderEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getUpdatedAt()).isEqualTo(NOW);
        assertThat(saved.getItems()).extracting("productId").containsExactly(" SKU ", "sku");
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.items()).extracting(OrderDetailsResult.Item::productId)
                .containsExactly(" SKU ", "sku");
    }

    @ParameterizedTest
    @MethodSource("invalidCreateCommands")
    void createRejectsEveryAggregateInvariant(CreateOrderCommand command, String expectedField) {
        assertThatThrownBy(() -> service().create(command))
                .isInstanceOf(InvalidOrderException.class)
                .satisfies(exception -> assertThat(((InvalidOrderException) exception).violations())
                        .extracting(InvalidOrderException.Violation::field)
                        .contains(expectedField));
        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsNullCommand() {
        assertThatThrownBy(() -> service().create(null))
                .isInstanceOf(InvalidOrderException.class)
                .satisfies(exception -> assertThat(((InvalidOrderException) exception).violations())
                        .containsExactly(new InvalidOrderException.Violation("$", "must be provided")));
    }

    @Test
    void getReturnsDetachedDetailOrNotFound() {
        OrderEntity entity = detailEntity(OrderStatus.SHIPPED);
        when(repository.findDetailById(ORDER_ID)).thenReturn(java.util.Optional.of(entity));
        assertThat(service().get(ORDER_ID).status()).isEqualTo("SHIPPED");

        UUID missing = UUID.randomUUID();
        when(repository.findDetailById(missing)).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> service().get(missing))
                .isInstanceOf(OrderNotFoundException.class);
        assertThatThrownBy(() -> service().get(null))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    void listUsesUnfilteredAndFilteredProjectionQueries() {
        OrderSummaryProjection projection = summaryProjection(OrderStatus.CANCELLED, 3);
        var page = new PageImpl<>(List.of(projection), PageRequest.of(0, 20), 1);
        when(repository.findSummaryPage(PageRequest.of(0, 20))).thenReturn(page);
        when(repository.findSummaryPageByStatus(OrderStatus.CANCELLED, PageRequest.of(0, 20)))
                .thenReturn(page);

        OrderPageResult unfiltered = service().list(null, 0, 20);
        OrderPageResult filtered = service().list("CANCELLED", 0, 20);

        assertThat(unfiltered.content()).hasSize(1);
        assertThat(filtered.content().getFirst().itemCount()).isEqualTo(3);
        assertThat(filtered.content().getFirst().status()).isEqualTo("CANCELLED");
    }

    @ParameterizedTest
    @MethodSource("invalidListInputs")
    void listRejectsInvalidPaginationOrStatus(String status, int page, int size, String field) {
        assertThatThrownBy(() -> service().list(status, page, size))
                .isInstanceOf(InvalidOrderException.class)
                .satisfies(exception -> assertThat(((InvalidOrderException) exception).violations())
                        .extracting(InvalidOrderException.Violation::field)
                        .contains(field));
    }

    @ParameterizedTest
    @MethodSource("legalManualTargets")
    void advanceUsesTheRequiredPersistedPredecessor(String targetName, OrderStatus expected) {
        OrderStatus target = OrderStatus.valueOf(targetName);
        OrderEntity updatedEntity = detailEntity(target);
        when(repository.updateStatusIfExpected(ORDER_ID, expected, target, NOW)).thenReturn(1);
        when(repository.findDetailById(ORDER_ID))
                .thenReturn(java.util.Optional.of(updatedEntity));

        assertThat(service().advanceStatus(ORDER_ID, targetName).status()).isEqualTo(targetName);
        verify(repository).updateStatusIfExpected(ORDER_ID, expected, target, NOW);
    }

    @ParameterizedTest
    @MethodSource("forbiddenManualTargets")
    void advanceRejectsKnownButForbiddenTargetsAsConflicts(String target) {
        assertThatThrownBy(() -> service().advanceStatus(ORDER_ID, target))
                .isInstanceOf(OrderStateConflictException.class);
        verify(repository, never()).updateStatusIfExpected(any(), any(), any(), any());
    }

    @ParameterizedTest
    @MethodSource("invalidStatusNames")
    void advanceRejectsUnknownStatusTextAsInvalidInput(String target) {
        assertThatThrownBy(() -> service().advanceStatus(ORDER_ID, target))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    void transitionClassifiesZeroRowsAsNotFoundOrConflict() {
        when(repository.updateStatusIfExpected(
                ORDER_ID, OrderStatus.PENDING, OrderStatus.PROCESSING, NOW)).thenReturn(0);
        when(repository.existsById(ORDER_ID)).thenReturn(false);
        assertThatThrownBy(() -> service().advanceStatus(ORDER_ID, "PROCESSING"))
                .isInstanceOf(OrderNotFoundException.class);

        when(repository.existsById(ORDER_ID)).thenReturn(true);
        assertThatThrownBy(() -> service().advanceStatus(ORDER_ID, "PROCESSING"))
                .isInstanceOf(OrderStateConflictException.class);
    }

    @Test
    void transitionRejectsImpossibleRepositoryOutcomes() {
        when(repository.updateStatusIfExpected(
                ORDER_ID, OrderStatus.PENDING, OrderStatus.PROCESSING, NOW)).thenReturn(2);
        assertThatThrownBy(() -> service().advanceStatus(ORDER_ID, "PROCESSING"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("multiple rows");

        when(repository.updateStatusIfExpected(
                ORDER_ID, OrderStatus.PENDING, OrderStatus.PROCESSING, NOW)).thenReturn(1);
        when(repository.findDetailById(ORDER_ID)).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> service().advanceStatus(ORDER_ID, "PROCESSING"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disappeared");
    }

    @Test
    void cancelUsesPendingCompareAndSet() {
        OrderEntity cancelledEntity = detailEntity(OrderStatus.CANCELLED);
        when(repository.updateStatusIfExpected(
                ORDER_ID, OrderStatus.PENDING, OrderStatus.CANCELLED, NOW)).thenReturn(1);
        when(repository.findDetailById(ORDER_ID))
                .thenReturn(java.util.Optional.of(cancelledEntity));

        assertThat(service().cancel(ORDER_ID).status()).isEqualTo("CANCELLED");
        verify(repository).updateStatusIfExpected(
                ORDER_ID, OrderStatus.PENDING, OrderStatus.CANCELLED, NOW);
        assertThatThrownBy(() -> service().cancel(null)).isInstanceOf(InvalidOrderException.class);
    }

    private OrderService service() {
        return new OrderService(repository, mapper, CLOCK);
    }

    private static OrderEntity detailEntity(OrderStatus status) {
        OrderEntity entity = mock(OrderEntity.class);
        when(entity.getId()).thenReturn(ORDER_ID);
        when(entity.getStatus()).thenReturn(status);
        when(entity.getItems()).thenReturn(List.of());
        when(entity.getCreatedAt()).thenReturn(NOW);
        when(entity.getUpdatedAt()).thenReturn(NOW);
        return entity;
    }

    private static OrderSummaryProjection summaryProjection(OrderStatus status, long itemCount) {
        OrderSummaryProjection projection = mock(OrderSummaryProjection.class);
        when(projection.getId()).thenReturn(ORDER_ID);
        when(projection.getStatus()).thenReturn(status);
        when(projection.getItemCount()).thenReturn(itemCount);
        when(projection.getCreatedAt()).thenReturn(NOW);
        when(projection.getUpdatedAt()).thenReturn(NOW);
        return projection;
    }

    private static Stream<Arguments> invalidCreateCommands() {
        List<CreateOrderCommand.Item> tooMany = new ArrayList<>();
        for (int index = 0; index < 101; index++) {
            tooMany.add(new CreateOrderCommand.Item("SKU-" + index, 1));
        }
        return Stream.of(
                Arguments.of(new CreateOrderCommand(null), "items"),
                Arguments.of(new CreateOrderCommand(List.of()), "items"),
                Arguments.of(new CreateOrderCommand(tooMany), "items"),
                Arguments.of(new CreateOrderCommand(Collections.singletonList(null)), "items[0]"),
                Arguments.of(new CreateOrderCommand(List.of(
                        new CreateOrderCommand.Item(null, 1))), "items[0].productId"),
                Arguments.of(new CreateOrderCommand(List.of(
                        new CreateOrderCommand.Item("   ", 1))), "items[0].productId"),
                Arguments.of(new CreateOrderCommand(List.of(
                        new CreateOrderCommand.Item("", 1))), "items[0].productId"),
                Arguments.of(new CreateOrderCommand(List.of(
                        new CreateOrderCommand.Item("😀".repeat(101), 1))), "items[0].productId"),
                Arguments.of(new CreateOrderCommand(List.of(
                        new CreateOrderCommand.Item("SKU\u0000BAD", 1))), "items[0].productId"),
                Arguments.of(new CreateOrderCommand(List.of(
                        new CreateOrderCommand.Item("SKU", 1),
                        new CreateOrderCommand.Item("SKU", 2))), "items[1].productId"),
                Arguments.of(new CreateOrderCommand(List.of(
                        new CreateOrderCommand.Item("SKU", 0))), "items[0].quantity"),
                Arguments.of(new CreateOrderCommand(List.of(
                        new CreateOrderCommand.Item("SKU", 1000))), "items[0].quantity"));
    }

    private static Stream<Arguments> invalidListInputs() {
        return Stream.of(
                Arguments.of(null, -1, 20, "page"),
                Arguments.of(null, 0, 0, "size"),
                Arguments.of(null, 0, 101, "size"),
                Arguments.of("", 0, 20, "status"),
                Arguments.of("pending", 0, 20, "status"),
                Arguments.of("UNKNOWN", 0, 20, "status"));
    }

    private static Stream<Arguments> legalManualTargets() {
        return Stream.of(
                Arguments.of("PROCESSING", OrderStatus.PENDING),
                Arguments.of("SHIPPED", OrderStatus.PROCESSING),
                Arguments.of("DELIVERED", OrderStatus.SHIPPED));
    }

    private static Stream<String> forbiddenManualTargets() {
        return Stream.of("PENDING", "CANCELLED");
    }

    private static Stream<String> invalidStatusNames() {
        return Stream.of(null, "", " ", "processing", "UNKNOWN");
    }
}
