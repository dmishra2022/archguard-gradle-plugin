package io.archguard.archunit.fixtures.hex.good.application;

import io.archguard.archunit.fixtures.hex.good.application.port.OrderPort;

/** Implementation of the port lives in application. */
public final class PlaceOrderService implements OrderPort {
    @Override public String place(String id) { return id; }
}
