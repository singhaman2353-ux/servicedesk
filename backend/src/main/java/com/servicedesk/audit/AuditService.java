package com.servicedesk.audit;

import com.servicedesk.user.User;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private static final int MAX_DETAILS_LENGTH = 500;
    private static final int MAX_IP_LENGTH = 45;

    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public AuditService(AuditLogRepository auditLogRepository, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    /**
     * Records an audit entry inside the caller's transaction, so the audit row commits
     * or rolls back together with the business change it describes.
     *
     * @param actor      who did it (null for system or anonymous events)
     * @param resourceId id of the affected resource (converted to text)
     * @param details    short human-readable note. NEVER pass passwords, tokens or secrets.
     */
    @Transactional
    public void record(User actor, AuditAction action, AuditResourceType resourceType,
                       Object resourceId, String details) {
        record(actor, action, resourceType, resourceId, details, null);
    }

    @Transactional
    public void record(User actor, AuditAction action, AuditResourceType resourceType,
                       Object resourceId, String details, String ipAddress) {
        save(actor, action, resourceType, resourceId, details, ipAddress);
    }

    /**
     * Records an entry in its OWN transaction, so it survives even if the caller's transaction
     * rolls back (or there is none). Used for security events such as failed logins.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(User actor, AuditAction action, AuditResourceType resourceType,
                                    Object resourceId, String details, String ipAddress) {
        save(actor, action, resourceType, resourceId, details, ipAddress);
    }

    private void save(User actor, AuditAction action, AuditResourceType resourceType,
                      Object resourceId, String details, String ipAddress) {
        auditLogRepository.save(new AuditLog(
                actor,
                action,
                resourceType,
                resourceId == null ? null : String.valueOf(resourceId),
                truncate(details, MAX_DETAILS_LENGTH),
                truncate(ipAddress, MAX_IP_LENGTH),
                Instant.now(clock)));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}