package com.example.ccbilling.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "administrators")
public class Administrator extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String username;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled;

    @Version
    private Integer version;

    protected Administrator() {
    }

    public Administrator(String username, String fullName, String passwordHash) {
        this.username = username;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.enabled = true;
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Long getId() { return id; }

    public String getUsername() { return username; }

    public String getFullName() { return fullName; }

    public String getPasswordHash() { return passwordHash; }

    public boolean isEnabled() { return enabled; }
}
