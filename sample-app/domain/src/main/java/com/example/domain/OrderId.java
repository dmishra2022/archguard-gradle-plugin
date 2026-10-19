package com.example.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Opaque order identifier. A value object — equality is value-based and the
 * only way to make one is to pass in a non-blank, non-null string.
 *
 * <p>Why not just {@code String}? Because a bare String has no invariants.
 * Six months from now someone will pass an empty string, a UUID in the wrong
 * format, or a product ID. A dedicated type is a one-line fix forever.
 */
public final class OrderId {
    private final String value;

    public OrderId(String value) {
        this.value = Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("OrderId must not be blank");
    }

    public static OrderId random() { return new OrderId(UUID.randomUUID().toString()); }

    public String value() { return value; }

    @Override public boolean equals(Object o) {
        return o instanceof OrderId that && value.equals(that.value);
    }
    @Override public int hashCode() { return value.hashCode(); }
    @Override public String toString() { return value; }
}
