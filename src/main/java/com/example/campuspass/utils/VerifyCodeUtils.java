package com.example.campuspass.utils;

import java.security.SecureRandom;

public class VerifyCodeUtils {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    public static String generateCode() {
        int code = SECURE_RANDOM.nextInt(1_000_000);
        return String.format("%06d", code);
    }

}
