package com.rkk.orderprocessing.order.application.exception;

import java.util.List;
import java.util.Objects;

/** Reports one or more invalid input fields without depending on HTTP classes. */
public final class InvalidOrderException extends RuntimeException {

    private final List<Violation> violations;

    /**
     * Creates one validation error containing every known field problem.
     *
     * @param violations the field names and safe error messages
     */
    public InvalidOrderException(List<Violation> violations) {
        super("The order request is invalid.");
        Objects.requireNonNull(violations, "violations");
        this.violations = List.copyOf(violations);
    }

    /**
     * Creates a validation error for one field.
     *
     * @param field the invalid field path
     * @param message the safe validation message
     * @return an exception containing the single violation
     */
    public static InvalidOrderException at(String field, String message) {
        return new InvalidOrderException(List.of(new Violation(field, message)));
    }

    /**
     * Returns an immutable copy of the validation problems.
     *
     * @return the field-level violations
     */
    public List<Violation> violations() {
        return violations;
    }

    /**
     * One invalid field and its safe message.
     *
     * @param field the invalid field path
     * @param message the message safe to return to a client
     */
    public record Violation(String field, String message) {
        public Violation {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(message, "message");
        }
    }
}
