package io.archguard.archunit.fixtures.clean.good.domain;

/** Pure domain entity: no framework imports, no reach-outs. */
public final class Order {
    private final String id;
    public Order(String id) { this.id = id; }
    public String id() { return id; }
}
