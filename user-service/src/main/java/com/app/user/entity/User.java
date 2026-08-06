package com.app.user.entity;

import com.app.user.model.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_email", columnNames = {"email"}),
                @UniqueConstraint(name = "uk_users_username", columnNames = {"username"})
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    private UUID id;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "full_name", nullable = false,  length = 254)
    private String fullName;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
