package io.archguard.archunit.fixtures.hex.bad_concrete_port.application.port;

/**
 * Deliberately broken: a port must be an interface, this is a concrete class.
 * The {@code portsAreInterfaces} rule must fire on this fixture.
 */
public final class ConcretePort {
    public String place(String id) { return id; }
}
