package com.app.user.repository;

import com.app.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);

    boolean existsByUsernameIgnoreCase(String username);

    List<User> findAllByIdIn(Collection<UUID> ids);
}
