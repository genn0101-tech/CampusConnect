package com.campusconnect.service;

import com.campusconnect.model.Group;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Hashes and checks group passphrases with BCrypt.
 * BCrypt is slow on purpose and adds a random "salt", so even if someone reads the database,
 * they cannot turn the hash back into the passphrase or look it up in a table.
 */
@Component
public class GroupPassphrase {

    public static final int MIN_LENGTH = 8;
    // BCrypt only uses the first 72 bytes, so longer passphrases are rejected
    public static final int MAX_BYTES = 72;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** Returns an error message, or null if the passphrase is acceptable. */
    public String validate(String passphrase) {
        if (passphrase == null || passphrase.length() < MIN_LENGTH) {
            return "Passphrase must be at least " + MIN_LENGTH + " characters.";
        }
        if (passphrase.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return "Passphrase is too long.";
        }
        return null;
    }

    public String hash(String passphrase) {
        return encoder.encode(passphrase);
    }

    public boolean matches(Group group, String input) {
        if (input == null || group.getPassphraseHash() == null) {
            return false; // a group without a passphrase cannot be unlocked
        }
        if (input.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return false; // the encoder would throw an error for this input
        }
        return encoder.matches(input, group.getPassphraseHash());
    }
}
