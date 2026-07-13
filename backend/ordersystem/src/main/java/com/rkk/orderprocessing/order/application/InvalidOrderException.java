package com.rkk.orderprocessing.order.application;

import java.util.List;
import java.util.Objects;

/** Controlled client-input failure with stable field-level violations. */
public final class InvalidOrderException extends RuntimeException {

    private final List<Violation> violations;

    /** Creates a validation failure from one or more identified input locations. */
    public InvalidOrderException(List<Violation> violations) {
        super("The order request is invalid.");
        Objects.requireNonNull(violations, "violations");
        this.violations = List.copyOf(violations);
    }

    /** Convenience factory for one field-level validation failure. */
    public static InvalidOrderException at(String field, String message) {
        return new InvalidOrderException(List.of(new Violation(field, message)));
    }

    /** Returns immutable violations for HTTP problem mapping. */
    public List<Violation> violations() {
        return violations;
    }

    /** Framework-neutral field and message pair. */
    public record Violation(String field, String message) {
        public Violation {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(message, "message");
        }
    }
}
