package io.archguard.archunit.fixtures.clean.bad_layering.domain;

import io.archguard.archunit.fixtures.clean.bad_layering.infrastructure.DatabaseThing;

/**
 * Deliberately broken: the domain reaches into infrastructure. The layered
 * rule must fire on this fixture.
 */
public final class DomainThatReachesOut {
    private final DatabaseThing db;
    public DomainThatReachesOut(DatabaseThing db) { this.db = db; }
}
