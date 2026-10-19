package com.example.domain;

import java.util.Objects;

/**
 * Aggregate root. Business invariants (status transitions, immutable id) live
 * here and nowhere else. The persistence layer rebuilds this type through a
 * repository port; the HTTP layer serialises it through a DTO. Both of those
 * are OUTSIDE the domain.
 */
public final class Order {
    public enum Status { PLACED, SHIPPED, CANCELLED }

    private final OrderId id;
    private Status status;

    public Order(OrderId id, Status status) {
        this.id = Objects.requireNonNull(id, "id");
        this.status = Objects.requireNonNull(status, "status");
    }

    public static Order place(OrderId id) { return new Order(id, Status.PLACED); }

    public void ship() {
        if (status != Status.PLACED) {
            throw new IllegalStateException("Can only ship PLACED orders; was " + status);
        }
        status = Status.SHIPPED;
    }

    public void cancel() {
        if (status == Status.SHIPPED) {
            throw new IllegalStateException("Cannot cancel a SHIPPED order");
        }
        status = Status.CANCELLED;
    }

    public OrderId id()      { return id; }
    public Status  status()  { return status; }
}
