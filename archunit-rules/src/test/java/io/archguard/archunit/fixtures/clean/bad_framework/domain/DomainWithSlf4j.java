package io.archguard.archunit.fixtures.clean.bad_framework.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deliberately broken: pulls slf4j into the domain. The framework-free rule
 * must fire on this fixture.
 */
public final class DomainWithSlf4j {
    private static final Logger log = LoggerFactory.getLogger(DomainWithSlf4j.class);
    public void shout(String s) { log.info(s); }
}
