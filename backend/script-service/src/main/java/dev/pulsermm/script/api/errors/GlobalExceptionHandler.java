package dev.pulsermm.script.api.errors;

import dev.pulsermm.script.application.ScriptAlreadyApprovedException;
import dev.pulsermm.script.application.ScriptNotFoundException;
import dev.pulsermm.script.application.ScriptRunNotFoundException;
import dev.pulsermm.script.application.ScriptRunResultNotFoundException;
import dev.pulsermm.script.application.SecretDecryptionException;
import dev.pulsermm.script.application.SecretEncryptionException;
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

    @ExceptionHandler(SecretEncryptionException.class)
    public ResponseEntity<ErrorResponse> handleSecretEncryption(SecretEncryptionException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("SECRET_ENCRYPTION_ERROR", "Failed to process script secrets"));
    }

    @ExceptionHandler(ScriptRunResultNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleScriptRunResultNotFound(ScriptRunResultNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("RESULT_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(SecretDecryptionException.class)
    public ResponseEntity<ErrorResponse> handleSecretDecryption(SecretDecryptionException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("SECRET_DECRYPTION_ERROR", "Failed to decrypt script secrets"));
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
