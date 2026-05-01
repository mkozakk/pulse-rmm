package dev.pulsermm.script.api;

import dev.pulsermm.script.application.ScriptAlreadyApprovedException;
import dev.pulsermm.script.application.ScriptNotFoundException;
import dev.pulsermm.script.application.ScriptRunNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ScriptNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleScriptNotFound(ScriptNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("SCRIPT_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ScriptAlreadyApprovedException.class)
    public ResponseEntity<ErrorResponse> handleScriptAlreadyApproved(ScriptAlreadyApprovedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("SCRIPT_ALREADY_APPROVED", ex.getMessage()));
    }

    @ExceptionHandler(ScriptRunNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleScriptRunNotFound(ScriptRunNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("SCRIPT_RUN_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationError(MethodArgumentNotValidException ex) {
        var message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + ", " + b)
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", message));
    }

    record ErrorResponse(String code, String message) {
    }
}
