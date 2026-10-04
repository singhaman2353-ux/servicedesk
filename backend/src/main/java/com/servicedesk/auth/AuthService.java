package com.servicedesk.auth;

import com.servicedesk.audit.AuditAction;
import com.servicedesk.audit.AuditResourceType;
import com.servicedesk.audit.AuditService;
import com.servicedesk.user.Role;
import com.servicedesk.user.User;
import com.servicedesk.user.UserResponse;
import com.servicedesk.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserService userService;
    private final AuditService auditService;

    public AuthService(UserService userService, AuditService auditService) {
        this.userService = userService;
        this.auditService = auditService;
    }

    /** Self-registration always yields a REQUESTER with no team. */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        User user = userService.createUser(
                request.name(), request.email(), request.password(), Role.REQUESTER, null);
        auditService.record(user, AuditAction.USER_REGISTERED, AuditResourceType.USER,
                user.getId(), "Self-registration");
        return UserResponse.from(user);
    }
}