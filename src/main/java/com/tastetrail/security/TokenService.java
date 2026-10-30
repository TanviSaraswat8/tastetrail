package com.tastetrail.security;

import com.google.gson.Gson;
import com.tastetrail.model.User;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Issues and checks signed login tokens (JSON Web Tokens, HS256) using only javax.crypto.
 * A token is header.payload.signature; changing any part of the payload breaks the signature.
 */
public class TokenService {
    private static final Gson GSON = new Gson();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_DEC = Base64.getUrlDecoder();

    private final byte[] secret;
    private final long ttlSeconds;

    public TokenService(String secret, long ttlSeconds) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
    }

    public String issue(User user) {
        String header = B64.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("id", user.getId());
        claims.put("role", user.getRole());
        claims.put("name", user.getName());
        claims.put("exp", System.currentTimeMillis() / 1000 + ttlSeconds);
        String payload = B64.encodeToString(GSON.toJson(claims).getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + "." + B64.encodeToString(sign(header + "." + payload));
    }

    /** Returns the Customer or Admin inside a valid, unexpired token – or null. */
    public User verify(String token) {
        if (token == null) return null;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return null;
        try {
            if (!MessageDigest.isEqual(B64_DEC.decode(parts[2]), sign(parts[0] + "." + parts[1]))) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> c = GSON.fromJson(new String(B64_DEC.decode(parts[1]), StandardCharsets.UTF_8), Map.class);
            if (((Number) c.get("exp")).longValue() < System.currentTimeMillis() / 1000) return null;
            return User.create(((Number) c.get("id")).intValue(), (String) c.get("role"), (String) c.get("name"), null, null);
        } catch (RuntimeException e) { return null; }
    }

    private byte[] sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
