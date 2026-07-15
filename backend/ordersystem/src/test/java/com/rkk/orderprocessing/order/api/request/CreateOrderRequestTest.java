package com.rkk.orderprocessing.order.api.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CreateOrderRequestTest {

    @Test
    void snapshotsItemsWithoutDiscardingValuesNeededByValidation() {
        List<CreateOrderItemRequest> source = new ArrayList<>();
        source.add(new CreateOrderItemRequest("SKU-1", 1));
        source.add(null);

        CreateOrderRequest request = new CreateOrderRequest(source);
        source.clear();

        assertThat(request.items())
                .containsExactly(new CreateOrderItemRequest("SKU-1", 1), null);
        assertThatThrownBy(() -> request.items().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void preservesANullListForJakartaValidation() {
        assertThat(new CreateOrderRequest(null).items()).isNull();
    }
}
