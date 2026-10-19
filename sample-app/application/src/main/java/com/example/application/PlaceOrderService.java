package com.example.application;

import com.example.domain.Order;
import com.example.domain.OrderId;
import com.example.port.in.PlaceOrderUseCase;
import com.example.port.out.OrderRepository;

/**
 * Use-case implementation. Talks only to its ports and to the domain —
 * nothing framework-y, nothing further out than itself.
 */
public final class PlaceOrderService implements PlaceOrderUseCase {

    private final OrderRepository repository;

    public PlaceOrderService(OrderRepository repository) {
        this.repository = repository;
    }

    @Override
    public Order placeOrder(OrderId id) {
        Order order = Order.place(id);
        repository.save(order);
        return order;
    }
}
