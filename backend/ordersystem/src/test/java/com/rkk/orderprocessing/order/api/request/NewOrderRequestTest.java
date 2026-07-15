package com.rkk.orderprocessing.order.api.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NewOrderRequestTest {

    @Test
    void snapshotsItemsWithoutDiscardingValuesNeededByValidation() {
        List<NewOrderRequest.Item> source = new ArrayList<>();
        source.add(new NewOrderRequest.Item("SKU-1", 1));
        source.add(null);

        NewOrderRequest request = new NewOrderRequest(source);
        source.clear();

        assertThat(request.items())
                .containsExactly(new NewOrderRequest.Item("SKU-1", 1), null);
        assertThatThrownBy(() -> request.items().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void preservesANullListForJakartaValidation() {
        assertThat(new NewOrderRequest(null).items()).isNull();
    }
}
