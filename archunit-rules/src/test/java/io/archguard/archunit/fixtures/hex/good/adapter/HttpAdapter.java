package io.archguard.archunit.fixtures.hex.good.adapter;

import io.archguard.archunit.fixtures.hex.good.application.port.OrderPort;

/** Legal: adapter talks to the port, not to any concrete core class. */
public final class HttpAdapter {
    private final OrderPort port;
    public HttpAdapter(OrderPort port) { this.port = port; }
}
