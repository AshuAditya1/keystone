package com.meridian.keystone.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The error contract.
 *
 * <p>Status codes are the part of an API that clients hard-code, so they are
 * asserted here rather than left to whatever Spring would have done by default.
 * The distinctions that matter: a business-rule refusal is 409 and worth
 * retrying after a reload, a validation failure is 400 and worth fixing, and a
 * 403 must never explain itself — saying "you cannot see work order 42" confirms
 * that work order 42 exists.
 *
 * <p>The handler is called directly with a {@link MockHttpServletRequest} instead
 * of through {@code @WebMvcTest}. A MockMvc slice would need the whole security
 * filter chain stood up to reach an advice that is pure translation logic.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("POST", "/api/work-orders/42/transition");
    }

    @Test
    @DisplayName("a validation failure is a 400 that names each bad field once")
    void validationFailureIsABadRequest() throws Exception {
        Method method = getClass().getDeclaredMethod("sampleHandlerMethod", String.class);
        BeanPropertyBindingResult binding =
                new BeanPropertyBindingResult(new Object(), "createWorkOrderRequest");
        binding.addError(new FieldError("createWorkOrderRequest", "title", "Title is required"));
        binding.addError(new FieldError("createWorkOrderRequest", "siteId", "Site is required"));
        // A second complaint about the same field must not overwrite the first.
        binding.addError(new FieldError("createWorkOrderRequest", "title", "and too long"));

        ResponseEntity<ApiError> response = handler.handleValidation(
                new MethodArgumentNotValidException(new MethodParameter(method, 0), binding),
                request);

        ApiError body = assertStatus(response, HttpStatus.BAD_REQUEST);
        assertThat(body.message()).isEqualTo("Validation failed");
        assertThat(body.fieldErrors())
                .containsEntry("title", "Title is required")
                .containsEntry("siteId", "Site is required")
                .hasSize(2);
    }

    @Test
    @DisplayName("unreadable JSON is a 400 that does not echo the parser's internals")
    void malformedBodyIsABadRequest() {
        ResponseEntity<ApiError> response = handler.handleUnreadable(
                new HttpMessageNotReadableException(
                        "Unexpected character ('}' (code 125))", emptyInputMessage()),
                request);

        ApiError body = assertStatus(response, HttpStatus.BAD_REQUEST);
        assertThat(body.message()).isEqualTo("Malformed request body");
        assertThat(body.fieldErrors()).isNull();
    }

    @Test
    @DisplayName("an enum typo in a query parameter lists the values that would have worked")
    void enumTypoListsTheAllowedValues() throws Exception {
        Method method = getClass().getDeclaredMethod("sampleHandlerMethod", String.class);
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "INPROGRESS", SampleStatus.class, "status",
                new MethodParameter(method, 0), new IllegalArgumentException("no enum constant"));

        ApiError body = assertStatus(handler.handleTypeMismatch(ex, request), HttpStatus.BAD_REQUEST);
        assertThat(body.message())
                .contains("'INPROGRESS' is not a valid SampleStatus")
                .contains("for parameter 'status'")
                .contains("Allowed values: NEW, IN_PROGRESS, DONE.");
    }

    @Test
    @DisplayName("a missing required parameter is a 400 that names it")
    void missingParameterIsABadRequest() {
        ApiError body = assertStatus(handler.handleMissingParam(
                new MissingServletRequestParameterException("customerId", "Long"), request),
                HttpStatus.BAD_REQUEST);
        assertThat(body.message()).isEqualTo("Required parameter 'customerId' is missing");
    }

    @Test
    @DisplayName("a bad sort field or other rejected argument is a 400, not a 500")
    void rejectedArgumentIsABadRequest() {
        ApiError body = assertStatus(handler.handleIllegalArgument(
                new IllegalArgumentException("Cannot sort by 'passwordHash'"), request),
                HttpStatus.BAD_REQUEST);
        assertThat(body.message()).isEqualTo("Cannot sort by 'passwordHash'");
    }

    @Test
    @DisplayName("bad credentials are a 401, and the reason is not narrowed down")
    void badCredentialsIsUnauthorized() {
        ApiError body = assertStatus(handler.handleBadCredentials(
                new BadCredentialsException("Email or password is incorrect"), request),
                HttpStatus.UNAUTHORIZED);
        // Deliberately not "no such user" or "wrong password" — either would
        // turn the login form into an account-enumeration tool.
        assertThat(body.message()).isEqualTo("Email or password is incorrect");
    }

    @Test
    @DisplayName("a deactivated account is a 403, distinct from a wrong password")
    void disabledAccountIsForbidden() {
        ApiError body = assertStatus(handler.handleDisabled(
                new DisabledException("User is disabled"), request), HttpStatus.FORBIDDEN);
        assertThat(body.message()).isEqualTo("Account is disabled");
    }

    @Test
    @DisplayName("a 403 never explains itself, because the explanation is the leak")
    void accessDeniedRevealsNothing() {
        ApiError body = assertStatus(handler.handleAccessDenied(
                new AccessDeniedException("You do not have access to work order 42"), request),
                HttpStatus.FORBIDDEN);
        assertThat(body.message())
                .isEqualTo("You do not have permission to perform this action");
        assertThat(body.message()).doesNotContain("42");
    }

    @Test
    @DisplayName("a missing record is a 404 that names what was looked for")
    void notFoundIsA404() {
        ApiError body = assertStatus(handler.handleNotFound(
                ResourceNotFoundException.of("Work order", 42L), request), HttpStatus.NOT_FOUND);
        assertThat(body.message()).contains("Work order").contains("42");
    }

    @Test
    @DisplayName("a typo'd URL is a 404, not a server error")
    void unmappedUrlIsA404() {
        ApiError body = assertStatus(handler.handleNoResource(
                new NoResourceFoundException(HttpMethod.GET, "/api/work-order"), request),
                HttpStatus.NOT_FOUND);
        assertThat(body.message()).startsWith("No endpoint ");
    }

    @Test
    @DisplayName("the wrong HTTP verb is a 405")
    void wrongVerbIsA405() {
        ApiError body = assertStatus(handler.handleMethodNotAllowed(
                new HttpRequestMethodNotSupportedException("DELETE"), request),
                HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(body.message()).isEqualTo("DELETE is not supported for this endpoint");
    }

    @Test
    @DisplayName("a broken business rule is a 409 and says exactly what was refused")
    void businessRuleIsAConflict() {
        ApiError body = assertStatus(handler.handleBusinessRule(
                new BusinessRuleException("Cannot move a work order from NEW to COMPLETED."),
                request), HttpStatus.CONFLICT);
        // This message reaches the user, so it must read as an instruction.
        assertThat(body.message()).isEqualTo("Cannot move a work order from NEW to COMPLETED.");
    }

    @Test
    @DisplayName("losing an edit race is a 409 that tells the user what to do next")
    void optimisticLockIsAConflict() {
        ApiError body = assertStatus(handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException("work_order", 42L), request),
                HttpStatus.CONFLICT);
        assertThat(body.message()).contains("Reload and try again.");
    }

    @Test
    @DisplayName("a database constraint is a 409 that does not leak the SQL")
    void constraintViolationIsAConflict() {
        ApiError body = assertStatus(handler.handleDataIntegrity(
                new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"uk_parts_sku\""),
                request), HttpStatus.CONFLICT);
        assertThat(body.message()).isEqualTo("That change conflicts with existing data.");
        assertThat(body.message()).doesNotContain("uk_parts_sku");
    }

    @Test
    @DisplayName("anything unexpected is a 500 that tells the client nothing")
    void unexpectedFailureIsA500() {
        ApiError body = assertStatus(handler.handleUnexpected(
                new IllegalStateException("connection pool exhausted at HikariPool-1"), request),
                HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(body.message()).isEqualTo("An unexpected error occurred");
        assertThat(body.message()).doesNotContain("HikariPool");
    }

    // ----------------------------------------------------------------- helpers

    /** Every error body carries the same five fields, whatever went wrong. */
    private ApiError assertStatus(ResponseEntity<ApiError> response, HttpStatus expected) {
        assertThat(response.getStatusCode()).isEqualTo(expected);
        ApiError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(expected.value());
        assertThat(body.error()).isEqualTo(expected.getReasonPhrase());
        assertThat(body.timestamp()).isNotNull();
        assertThat(body.message()).isNotBlank();
        assertThat(body.path()).isEqualTo("/api/work-orders/42/transition");
        return body;
    }

    private HttpInputMessage emptyInputMessage() {
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(new byte[0]);
            }

            @Override
            public HttpHeaders getHeaders() {
                return new HttpHeaders();
            }
        };
    }

    /** Only ever reflected on, to build the exceptions Spring would have built. */
    @SuppressWarnings("unused")
    private void sampleHandlerMethod(String status) {
    }

    private enum SampleStatus {
        NEW, IN_PROGRESS, DONE
    }
}
