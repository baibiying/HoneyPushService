package com.honeypush.service.auth;

import org.bouncycastle.crypto.generators.SCrypt;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

@Component
public class NodeScryptPasswordHasher {

    private static final int SALT_BYTES = 16;
    private static final int KEY_LENGTH = 64;
    private static final int N = 16384;
    private static final int R = 8;
    private static final int P = 1;

    private final SecureRandom secureRandom = new SecureRandom();

    public String hashPassword(String password) {
        String saltHex = randomSaltHex();
        byte[] derived = scrypt(password, saltHex);
        return "scrypt:" + saltHex + ":" + HexFormat.of().formatHex(derived);
    }

    public boolean verifyPassword(String password, String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            return false;
        }
        String[] parts = passwordHash.split(":");
        if (parts.length != 3 || !"scrypt".equals(parts[0])) {
            return false;
        }
        String saltHex = parts[1];
        String expectedHex = parts[2];
        byte[] derived = scrypt(password, saltHex);
        byte[] expected;
        try {
            expected = HexFormat.of().parseHex(expectedHex);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (derived.length != expected.length) {
            return false;
        }
        return MessageDigest.isEqual(derived, expected);
    }

    private byte[] scrypt(String password, String saltHexString) {
        byte[] salt = saltHexString.getBytes(StandardCharsets.UTF_8);
        return SCrypt.generate(password.getBytes(StandardCharsets.UTF_8), salt, N, R, P, KEY_LENGTH);
    }

    private String randomSaltHex() {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }
}
