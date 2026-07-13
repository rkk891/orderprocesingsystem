package com.rkk.orderprocessing.shared.api;

import com.rkk.orderprocessing.order.application.InvalidOrderException;
import com.rkk.orderprocessing.order.application.OrderNotFoundException;
import com.rkk.orderprocessing.order.application.OrderStateConflictException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Central RFC 9457 mapper with stable safe descriptions and deterministic field violations.
 * Framework exception messages, SQL details, request bodies, and stack traces are never copied to
 * the response.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final Comparator<ApiViolation> VIOLATION_ORDER =
            Comparator.comparing(ApiViolation::field).thenComparing(ApiViolation::message);

    private static final ProblemSpec INVALID_REQUEST = new ProblemSpec(
            HttpStatus.BAD_REQUEST,
            "urn:problem:invalid-request",
            "Invalid request",
            "INVALID_REQUEST",
            "The request is malformed or violates validation rules.");
    private static final ProblemSpec ORDER_NOT_FOUND = new ProblemSpec(
            HttpStatus.NOT_FOUND,
            "urn:problem:order-not-found",
            "Order not found",
            "ORDER_NOT_FOUND",
            "The requested order does not exist.");
    private static final ProblemSpec RESOURCE_NOT_FOUND = new ProblemSpec(
            HttpStatus.NOT_FOUND,
            "urn:problem:resource-not-found",
            "Resource not found",
            "RESOURCE_NOT_FOUND",
            "The requested resource does not exist.");
    private static final ProblemSpec ORDER_STATE_CONFLICT = new ProblemSpec(
            HttpStatus.CONFLICT,
            "urn:problem:order-state-conflict",
            "Order state conflict",
            "ORDER_STATE_CONFLICT",
            "The order cannot make the requested transition.");
    private static final ProblemSpec METHOD_NOT_ALLOWED = new ProblemSpec(
            HttpStatus.METHOD_NOT_ALLOWED,
            "urn:problem:method-not-allowed",
            "Method not allowed",
            "METHOD_NOT_ALLOWED",
            "The HTTP method is not supported for this resource.");
    private static final ProblemSpec NOT_ACCEPTABLE = new ProblemSpec(
            HttpStatus.NOT_ACCEPTABLE,
            "urn:problem:not-acceptable",
            "Not acceptable",
            "NOT_ACCEPTABLE",
            "The requested response representation is not available.");
    private static final ProblemSpec UNSUPPORTED_MEDIA_TYPE = new ProblemSpec(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "urn:problem:unsupported-media-type",
            "Unsupported media type",
            "UNSUPPORTED_MEDIA_TYPE",
            "The request media type is not supported.");
    private static final ProblemSpec INTERNAL_ERROR = new ProblemSpec(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "urn:problem:internal-error",
            "Internal error",
            "INTERNAL_ERROR",
            "An unexpected error occurred.");

    /** Maps application and controller validation failures to deterministic 400 responses. */
    @ExceptionHandler(InvalidOrderException.class)
    public ResponseEntity<ProblemDetail> handleInvalidOrder(
            InvalidOrderException exception,
            HttpServletRequest request) {
        List<ApiViolation> violations = exception.violations().stream()
                .map(violation -> new ApiViolation(violation.field(), violation.message()))
                .toList();
        return response(INVALID_REQUEST, request, violations, null);
    }

    /** Covers request-body and other binding failures with stable client field paths. */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ProblemDetail> handleBind(
            BindException exception,
            HttpServletRequest request) {
        return response(INVALID_REQUEST, request, bindingViolations(exception), null);
    }

    /** Maps malformed path/query scalar conversion, notably invalid UUIDs. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        String message = UUID.class.equals(exception.getRequiredType())
                ? "must be a valid UUID"
                : "has an invalid value";
        return response(
                INVALID_REQUEST,
                request,
                List.of(new ApiViolation(exception.getName(), message)),
                null);
    }

    /** Uses an empty violation list when malformed JSON has no trustworthy field location. */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(
            HttpServletRequest request) {
        return response(INVALID_REQUEST, request, List.of(), null);
    }

    /** Distinguishes an absent valid order ID from malformed input. */
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleOrderNotFound(HttpServletRequest request) {
        return response(ORDER_NOT_FOUND, request, List.of(), null);
    }

    /** Maps rejected or concurrently lost lifecycle mutations. */
    @ExceptionHandler(OrderStateConflictException.class)
    public ResponseEntity<ProblemDetail> handleStateConflict(HttpServletRequest request) {
        return response(ORDER_STATE_CONFLICT, request, List.of(), null);
    }

    /** Provides the same problem shape for unmapped static or API resources. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleResourceNotFound(HttpServletRequest request) {
        return response(RESOURCE_NOT_FOUND, request, List.of(), null);
    }

    /** Returns 405 with the required Allow header when Spring knows supported methods. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        if (exception.getSupportedHttpMethods() != null) {
            headers.setAllow(exception.getSupportedHttpMethods());
        }
        return response(METHOD_NOT_ALLOWED, request, List.of(), headers);
    }

    /** Maps failed response content negotiation to a stable 406 response. */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ProblemDetail> handleNotAcceptable(HttpServletRequest request) {
        return response(NOT_ACCEPTABLE, request, List.of(), null);
    }

    /** Maps an unsupported request Content-Type to a stable 415 response. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedMediaType(HttpServletRequest request) {
        return response(UNSUPPORTED_MEDIA_TYPE, request, List.of(), null);
    }

    /** Sanitizes every unexpected failure while retaining server-side correlation evidence. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(
            Exception exception,
            HttpServletRequest request) {
        String traceId = traceId(request);
        LOGGER.error(
                "Unhandled request failure traceId={} method={} path={} exceptionType={}",
                traceId,
                request.getMethod(),
                request.getRequestURI(),
                exception.getClass().getName());
        return response(INTERNAL_ERROR, request, List.of(), null);
    }

    private static List<ApiViolation> bindingViolations(BindException exception) {
        List<ApiViolation> violations = new ArrayList<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            violations.add(new ApiViolation(
                    fieldError.getField(),
                    defaultMessage(fieldError)));
        }
        exception.getBindingResult().getGlobalErrors().forEach(error ->
                violations.add(new ApiViolation("$", defaultMessage(error))));
        return violations;
    }

    private static String defaultMessage(DefaultMessageSourceResolvable error) {
        String message = error.getDefaultMessage();
        return message == null || message.isBlank() ? "has an invalid value" : message;
    }

    private static ResponseEntity<ProblemDetail> response(
            ProblemSpec spec,
            HttpServletRequest request,
            List<ApiViolation> unsortedViolations,
            HttpHeaders additionalHeaders) {
        List<ApiViolation> violations = unsortedViolations.stream().sorted(VIOLATION_ORDER).toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(spec.status(), spec.detail());
        problem.setType(URI.create(spec.type()));
        problem.setTitle(spec.title());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", spec.code());
        problem.setProperty("traceId", traceId(request));
        problem.setProperty("violations", violations);

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(spec.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (additionalHeaders != null) {
            builder.headers(additionalHeaders);
        }
        return builder.body(problem);
    }

    private static String traceId(HttpServletRequest request) {
        return RequestTraceFilter.getOrCreateTraceId(request);
    }

    private record ApiViolation(String field, String message) {}

    private record ProblemSpec(
            HttpStatus status,
            String type,
            String title,
            String code,
            String detail) {}
}
