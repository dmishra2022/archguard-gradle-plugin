package io.archguard.archunit.fixtures.clean.good.application;

import io.archguard.archunit.fixtures.clean.good.domain.Order;

/** Legal: application orchestrates domain. */
public final class PlaceOrder {
    public Order run(String id) { return new Order(id); }
}
