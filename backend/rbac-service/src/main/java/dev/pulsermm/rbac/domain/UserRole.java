package dev.pulsermm.rbac.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "user_roles")
public class UserRole {

    @EmbeddedId
    private UserRoleId id;

    public UserRole() {}

    public UserRole(UserRoleId id) {
        this.id = id;
    }

    public UserRoleId getId() { return id; }
}
