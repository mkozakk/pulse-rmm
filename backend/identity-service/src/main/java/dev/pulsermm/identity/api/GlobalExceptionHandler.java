package dev.pulsermm.identity.api;

import dev.pulsermm.identity.api.dto.ErrorResponse;
import dev.pulsermm.identity.api.errors.BootstrapClosedException;
import dev.pulsermm.identity.api.errors.UsernameTakenException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getAllErrors().stream()
            .map(e -> e instanceof FieldError fe ? fe.getField() + " " + fe.getDefaultMessage() : e.getDefaultMessage())
            .findFirst()
            .orElse("Validation failed");
        return new ErrorResponse("validation_failed", message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMalformedJson(HttpMessageNotReadableException ex) {
        return new ErrorResponse("validation_failed", "Malformed request body");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ErrorResponse handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return new ErrorResponse("unsupported_media_type", ex.getMessage());
    }

    @ExceptionHandler(BootstrapClosedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleBootstrapClosed(BootstrapClosedException ex) {
        return new ErrorResponse("registration_disabled", ex.getMessage());
    }

    @ExceptionHandler(UsernameTakenException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleUsernameTaken(UsernameTakenException ex) {
        return new ErrorResponse("username_taken", ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDataIntegrity(DataIntegrityViolationException ex) {
        // Unique constraint hit during concurrent bootstrap inserts
        return new ErrorResponse("registration_disabled", "Registration is disabled");
    }
}
