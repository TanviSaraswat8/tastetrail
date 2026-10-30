package com.tastetrail.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Hashes passwords with PBKDF2-HMAC-SHA256 (JDK only). Stored format:
 *   pbkdf2_sha256$iterations$salt$hash
 * A new random salt is used for every password, so two users with the same password get different hashes.
 */
public class PasswordHasher {
    private static final SecureRandom RNG = new SecureRandom();
    private final int iterations;

    public PasswordHasher(int iterations) { this.iterations = iterations; }

    public String hash(String password) {
        byte[] salt = new byte[16];
        RNG.nextBytes(salt);
        return "pbkdf2_sha256$" + iterations + "$" + Base64.getEncoder().encodeToString(salt) + "$"
            + Base64.getEncoder().encodeToString(pbkdf2(password, salt, iterations));
    }

    /** Re-computes the hash with the stored salt and compares in constant time. */
    public boolean verify(String password, String stored) {
        if (password == null || stored == null) return false;
        String[] p = stored.split("\\$");
        if (p.length != 4) return false;
        byte[] salt = Base64.getDecoder().decode(p[2]);
        byte[] expected = Base64.getDecoder().decode(p[3]);
        return MessageDigest.isEqual(expected, pbkdf2(password, salt, Integer.parseInt(p[1])));
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
