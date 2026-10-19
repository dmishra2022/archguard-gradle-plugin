package io.archguard.archunit.fixtures.clean.good.interfaces;

import io.archguard.archunit.fixtures.clean.good.application.PlaceOrder;

/** Legal: interface layer calls into application. */
public final class OrderController {
    private final PlaceOrder useCase;
    public OrderController(PlaceOrder useCase) { this.useCase = useCase; }
    public String handle(String id) { return useCase.run(id).id(); }
}
