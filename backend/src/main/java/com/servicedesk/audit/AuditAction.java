package com.servicedesk.audit;

/** What happened. Stored as text; add values here as features arrive (no migration needed). */
public enum AuditAction {
    USER_REGISTERED,
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGIN_THROTTLED,
    REGISTER_THROTTLED,
    USER_CREATED,
    USER_UPDATED,
    TEAM_CREATED,
    TEAM_UPDATED,
    ADMIN_BOOTSTRAPPED
}