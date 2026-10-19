package com.example.adapter.rest;

import com.example.domain.OrderId;
import com.example.port.in.PlaceOrderUseCase;

/**
 * Inbound adapter. Talks to the application only through its inbound port
 * ({@link PlaceOrderUseCase}). This is what ArchGuard's HEX-002 rule enforces
 * at build time, and what the ArchUnit test in src/test asserts at test time.
 */
public final class OrderController {

    private final PlaceOrderUseCase useCase;

    public OrderController(PlaceOrderUseCase useCase) {
        this.useCase = useCase;
    }

    /** Hand-rolled "controller method" — in a real app the framework calls this. */
    public String handlePlaceOrder(String rawId) {
        var order = useCase.placeOrder(new OrderId(rawId));
        return order.id().value();
    }
}
