package com.servicedesk.user;

import com.servicedesk.team.Team;
import java.time.Instant;

/**
 * What the API returns about a user. There is deliberately no password or hash field.
 * Call from(...) inside a transaction: it touches the lazily loaded team.
 */
public record UserResponse(
        Long id,
        String name,
        String email,
        Role role,
        boolean active,
        Long teamId,
        String teamName,
        Instant createdAt) {

    public static UserResponse from(User user) {
        Team team = user.getTeam();
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.isActive(),
                team == null ? null : team.getId(),
                team == null ? null : team.getName(),
                user.getCreatedAt());
    }
}