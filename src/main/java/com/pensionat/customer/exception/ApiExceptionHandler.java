package com.pensionat.customer.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validationFailed(MethodArgumentNotValidException e, HttpServletRequest request) {
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(), "message",
                        f.getDefaultMessage() == null ? "Invalid value" : f.getDefaultMessage()))
                .toList();

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid input",
                "One or more fields are invalid", "VALIDATION_FAILED",
                "/problems/validation-failed", request);
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler({InvalidRequestException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail invalidRequest(Exception e, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request",
                e instanceof InvalidRequestException ? e.getMessage() : "Invalid parameter",
                "INVALID_REQUEST", "/problems/invalid-request", request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail invalidCredentials(HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "Login failed",
                "Wrong username or password", "INVALID_CREDENTIALS",
                "/problems/invalid-credentials", request);
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    ProblemDetail customerNotFound(CustomerNotFoundException e, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Customer was not found", e.getMessage(),
                "CUSTOMER_NOT_FOUND", "/problems/customer-not-found", request);
    }

    @ExceptionHandler(CustomerHasActiveBookingsException.class)
    ProblemDetail customerHasActiveBookings(CustomerHasActiveBookingsException e, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Customer cannot be deleted",
                e.getMessage(), "CUSTOMER_HAS_ACTIVE_BOOKINGS",
                "/problems/customer-has-active-bookings", request);
        problem.setProperty("activeBookingCount", e.getActiveBookingCount());
        return problem;
    }

    @ExceptionHandler(BookingServiceUnavailableException.class)
    ResponseEntity<ProblemDetail> bookingServiceUnavailable(HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE,
                "Service is unavailable right now",
                "We could not check the customer's bookings right now, please try again later",
                "BOOKING_SERVICE_UNAVAILABLE", "/problems/booking-service-unavailable", request);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "5")
                .body(problem);
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail,
                                  String errorCode, String type, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(type));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", errorCode);
        return problem;
    }
}
