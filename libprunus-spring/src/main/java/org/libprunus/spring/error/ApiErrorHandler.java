package org.libprunus.spring.error;

import org.jspecify.annotations.Nullable;
import org.libprunus.core.error.ApiErrorException;
import org.libprunus.core.error.ErrorCategory;
import org.libprunus.core.error.FallbackErrorCode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Library fallback that renders every exception reaching the dispatcher as an RFC 9457
 * {@link ProblemDetail} carrying a stable {@code code}: an {@link ApiErrorException} maps its
 * {@link ErrorCategory} to an HTTP status; built-in Spring MVC exceptions keep their own status; any
 * other exception becomes a generic INTERNAL problem whose body never carries the internal message
 * (full diagnostics are only logged). Registered at {@link Ordered#LOWEST_PRECEDENCE} so an
 * application's own {@code @ControllerAdvice} always takes precedence; the auto-configuration backs
 * off via {@code @ConditionalOnMissingBean}, and because this extends
 * {@link ResponseEntityExceptionHandler} Boot's own {@code ProblemDetailsExceptionHandler} (when
 * enabled) backs off too.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class ApiErrorHandler extends ResponseEntityExceptionHandler {

    private static final String CODE_PROPERTY = "code";

    @ExceptionHandler(ApiErrorException.class)
    public @Nullable ResponseEntity<Object> handleApiError(ApiErrorException exception, WebRequest request) {
        HttpStatus status = httpStatusFor(exception.errorCode().category());
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setDetail(exception.getMessage());
        problem.setProperty(CODE_PROPERTY, exception.errorCode().code());
        return handleExceptionInternal(exception, problem, new HttpHeaders(), status, request);
    }

    @ExceptionHandler(Exception.class)
    public @Nullable ResponseEntity<Object> handleUnexpected(Exception exception, WebRequest request) {
        logger.error("Unhandled exception mapped to a generic INTERNAL problem detail", exception);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setProperty(CODE_PROPERTY, FallbackErrorCode.INTERNAL.code());
        return handleExceptionInternal(
                exception, problem, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    // All handled exceptions reach createResponseEntity with their body finalized — built-in MVC ones are
    // still null in handleExceptionInternal — so this is the one place the uniform code can be stamped.
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            var properties = problem.getProperties();
            if (properties == null || !properties.containsKey(CODE_PROPERTY)) {
                problem.setProperty(CODE_PROPERTY, deriveCodeFromStatus(statusCode));
            }
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private static HttpStatus httpStatusFor(ErrorCategory category) {
        return switch (category) {
            case INVALID_ARGUMENT, FAILED_PRECONDITION -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case RESOURCE_EXHAUSTED -> HttpStatus.TOO_MANY_REQUESTS;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private static String deriveCodeFromStatus(HttpStatusCode statusCode) {
        HttpStatus resolved = HttpStatus.resolve(statusCode.value());
        return resolved != null ? resolved.name() : Integer.toString(statusCode.value());
    }
}
