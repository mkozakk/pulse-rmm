package dev.pulsermm.enrolment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "endpoints", schema = "enrolment")
public class Endpoint {
    @Id
    private UUID id;

    @Column(nullable = false, length = 255)
    private String hostname;

    @Column(nullable = false, length = 64)
    private String os;

    @Column(nullable = false, length = 32)
    private String arch;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "public_key", nullable = false, unique = true)
    private byte[] publicKey;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    public Endpoint() {}

    public Endpoint(UUID id, String hostname, String os, String arch, UUID groupId, byte[] publicKey, Instant enrolledAt, Instant lastSeen) {
        this.id = id;
        this.hostname = hostname;
        this.os = os;
        this.arch = arch;
        this.groupId = groupId;
        this.publicKey = publicKey;
        this.enrolledAt = enrolledAt;
        this.lastSeen = lastSeen;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public String getArch() {
        return arch;
    }

    public void setArch(String arch) {
        this.arch = arch;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(byte[] publicKey) {
        this.publicKey = publicKey;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }

    public void setEnrolledAt(Instant enrolledAt) {
        this.enrolledAt = enrolledAt;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }
}
