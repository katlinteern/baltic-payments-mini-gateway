package com.baltic.gateway.identity.infrastructure.login;

import java.util.Optional;
import java.util.UUID;
import com.baltic.gateway.identity.domain.login.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
}
