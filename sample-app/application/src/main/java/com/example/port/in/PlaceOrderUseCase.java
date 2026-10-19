package com.example.port.in;

import com.example.domain.Order;
import com.example.domain.OrderId;

/**
 * Inbound port — the contract driving adapters (REST, CLI, scheduled jobs)
 * use to drive the application. The HTTP adapter talks to this interface,
 * never to the concrete {@link com.example.application.PlaceOrderService}.
 *
 * <p>Why does this live under {@code com.example.port.*} and not under
 * {@code com.example.application.port.*}? ArchGuard assigns roles by package
 * glob; nesting ports inside application causes
 * {@code com.example.application..} and {@code com.example.application.port..}
 * to both match, which is an ambiguous role assignment. Keeping ports as a
 * top-level sibling matches the ArchGuard clean example and avoids the
 * ambiguity.
 */
public interface PlaceOrderUseCase {
    Order placeOrder(OrderId id);
}
