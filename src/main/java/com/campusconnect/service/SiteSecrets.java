package com.campusconnect.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * The two site-wide secrets, both read from environment variables:
 * - GROUP_CREATION_CODE: needed to create a new group
 * - ADMIN_PASSPHRASE: needed to open the developer page (/admin)
 * In production (app.require-secrets=true) the app refuses to start if either one is missing.
 */
@Component
public class SiteSecrets {

    private final String groupCreationCode;
    private final String adminPassphrase;

    public SiteSecrets(@Value("${app.group-creation-code}") String groupCreationCode,
                       @Value("${app.admin-passphrase}") String adminPassphrase,
                       @Value("${app.require-secrets}") boolean requireSecrets) {
        if (requireSecrets && (groupCreationCode.isBlank() || adminPassphrase.isBlank())) {
            // Throwing here stops the whole app from starting
            throw new IllegalStateException(
                    "GROUP_CREATION_CODE and ADMIN_PASSPHRASE environment variables must be set");
        }
        this.groupCreationCode = groupCreationCode;
        this.adminPassphrase = adminPassphrase;
    }

    public boolean isGroupCreationCode(String input) {
        return sameText(groupCreationCode, input);
    }

    public boolean isAdminPassphrase(String input) {
        return sameText(adminPassphrase, input);
    }

    private boolean sameText(String expected, String input) {
        if (input == null || expected.isBlank()) {
            return false; // not configured = nobody can get in
        }
        // MessageDigest.isEqual takes the same time no matter where the first wrong character is,
        // so an attacker cannot guess the secret by measuring response time.
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                input.getBytes(StandardCharsets.UTF_8));
    }
}
