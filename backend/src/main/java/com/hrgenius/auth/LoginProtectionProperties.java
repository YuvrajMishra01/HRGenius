package com.hrgenius.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Login abuse protection settings (Phase 24). Bound from application.yml so
 * the threshold and lockout window are environment-tunable — never hardcoded
 * in the codebase. Mutable setters are part of the standard
 * ConfigurationProperties contract (and let tests shorten the lockout window
 * deterministically instead of sleeping).
 */
@Component
@ConfigurationProperties(prefix = "app.login-protection")
public class LoginProtectionProperties {

    /** Master switch — protection can be disabled for exotic test setups. */
    private boolean enabled = true;

    /** Failed attempts allowed per email before a temporary lockout. */
    private int maxFailedAttempts = 5;

    /** How long the temporary lockout lasts once triggered. */
    private Duration lockoutDuration = Duration.ofMinutes(10);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }

    public void setMaxFailedAttempts(int maxFailedAttempts) {
        this.maxFailedAttempts = maxFailedAttempts;
    }

    public Duration getLockoutDuration() {
        return lockoutDuration;
    }

    public void setLockoutDuration(Duration lockoutDuration) {
        this.lockoutDuration = lockoutDuration;
    }
}
