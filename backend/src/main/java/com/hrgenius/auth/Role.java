package com.hrgenius.auth;

/**
 * Application roles. Ordered from most to least privileged.
 * Spring Security authorities are derived as "ROLE_" + name().
 */
public enum Role {
    ADMIN,
    HR,
    MANAGER,
    EMPLOYEE
}
