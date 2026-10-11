package com.example.campuspass.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilsTest {

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        String secret = Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded());
        jwtUtils = new JwtUtils(secret);
    }

    @Test
    void generatesAndParsesSignedToken() {
        String sid = "3&123e4567-e89b-12d3-a456-426614174000";

        String token = jwtUtils.generateToken(Map.of("sid", sid));
        Claims claims = jwtUtils.parseToken(token);

        assertEquals(sid, claims.get("sid", String.class));
        assertTrue(claims.getExpiration().after(claims.getIssuedAt()));
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String token = jwtUtils.generateToken(Map.of("sid", "0&123e4567-e89b-12d3-a456-426614174000"));
        String anotherSecret = Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded());
        JwtUtils anotherJwtUtils = new JwtUtils(anotherSecret);

        assertThrows(JwtException.class, () -> anotherJwtUtils.parseToken(token));
    }

    @Test
    void rejectsMalformedToken() {
        assertThrows(JwtException.class, () -> jwtUtils.parseToken("not-a-jwt"));
    }
}
