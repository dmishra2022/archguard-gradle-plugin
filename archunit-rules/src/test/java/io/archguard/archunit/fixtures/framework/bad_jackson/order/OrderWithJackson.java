package io.archguard.archunit.fixtures.framework.bad_jackson.order;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Deliberately broken: Jackson has leaked into a non-adapter package. */
public final class OrderWithJackson {
    @JsonProperty("id") public final String id;
    public OrderWithJackson(String id) { this.id = id; }
}
