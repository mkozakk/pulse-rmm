package dev.pulsermm.identity.api.errors;

public class UsernameTakenException extends RuntimeException {
    public UsernameTakenException(String username) {
        super("Username already taken: " + username);
    }
}
