package com.servicedesk.throttle;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginThrottleRepository extends JpaRepository<LoginThrottle, Long> {

    Optional<LoginThrottle> findByScopeAndKeyHash(ThrottleScope scope, String keyHash);

    /** SELECT ... FOR UPDATE: concurrent failures for the same key are serialized. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from LoginThrottle t where t.scope = :scope and t.keyHash = :keyHash")
    Optional<LoginThrottle> findForUpdate(@Param("scope") ThrottleScope scope, @Param("keyHash") String keyHash);

    /** Atomic "create if missing". Safe when two requests race to insert the same key. */
    @Modifying
    @Query(value = "INSERT INTO login_throttle (scope, key_hash, failure_count, window_start, locked_until, updated_at) "
            + "VALUES (:scope, :keyHash, 0, :now, NULL, :now) ON DUPLICATE KEY UPDATE id = id",
            nativeQuery = true)
    int insertIfAbsent(@Param("scope") String scope, @Param("keyHash") String keyHash, @Param("now") Instant now);

    @Modifying
    @Query("delete from LoginThrottle t where t.scope = :scope and t.keyHash = :keyHash")
    int deleteByScopeAndKeyHash(@Param("scope") ThrottleScope scope, @Param("keyHash") String keyHash);
}