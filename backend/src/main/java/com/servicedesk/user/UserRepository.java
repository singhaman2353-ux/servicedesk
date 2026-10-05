package com.servicedesk.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Loads the team together with the user, so the result is safe to use outside a transaction. */
    @EntityGraph(attributePaths = "team")
    Optional<User> findWithTeamByEmail(String email);

    @EntityGraph(attributePaths = "team")
    Optional<User> findWithTeamById(Long id);
}