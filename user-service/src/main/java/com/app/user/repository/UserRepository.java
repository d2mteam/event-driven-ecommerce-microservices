package com.app.user.repository;

import com.app.user.entity.User;
import com.app.user.model.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    List<User> findAllByIdIn(Collection<UUID> ids);

    @Query("""
            select user
            from User user
            where (
                :query is null
                or lower(user.fullName) like lower(concat('%', :query, '%'))
                or lower(user.email) like lower(concat('%', :query, '%'))
                or lower(user.username) like lower(concat('%', :query, '%'))
            )
              and (:status is null or user.status = :status)
            """)
    Page<User> searchAdminUsers(
            @Param("query") String query,
            @Param("status") UserStatus status,
            Pageable pageable
    );
}
