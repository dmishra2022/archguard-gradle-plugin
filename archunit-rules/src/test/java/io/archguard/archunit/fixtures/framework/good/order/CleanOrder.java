package io.archguard.archunit.fixtures.framework.good.order;

/** No framework imports anywhere. */
public final class CleanOrder {
    private final String id;
    public CleanOrder(String id) { this.id = id; }
    public String id() { return id; }
}
