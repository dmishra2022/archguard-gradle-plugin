package com.example.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test void placed_order_can_be_shipped() {
        Order o = Order.place(OrderId.random());
        o.ship();
        assertThat(o.status()).isEqualTo(Order.Status.SHIPPED);
    }

    @Test void shipped_order_cannot_be_cancelled() {
        Order o = Order.place(OrderId.random());
        o.ship();
        assertThatThrownBy(o::cancel).isInstanceOf(IllegalStateException.class);
    }

    @Test void placed_order_can_be_cancelled() {
        Order o = Order.place(OrderId.random());
        o.cancel();
        assertThat(o.status()).isEqualTo(Order.Status.CANCELLED);
    }
}
