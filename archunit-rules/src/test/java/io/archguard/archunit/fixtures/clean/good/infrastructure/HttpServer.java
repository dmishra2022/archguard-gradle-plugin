package io.archguard.archunit.fixtures.clean.good.infrastructure;

import io.archguard.archunit.fixtures.clean.good.interfaces.OrderController;

/** Legal: infrastructure wires up the interface layer. */
public final class HttpServer {
    private final OrderController controller;
    public HttpServer(OrderController controller) { this.controller = controller; }
}
