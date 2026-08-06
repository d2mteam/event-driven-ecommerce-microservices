package com.app.user.service;

import com.app.user.exception.JwtException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {

    public static final String ACCESS_TOKEN_TYPE = "access_token";
    public static final String REFRESH_TOKEN_TYPE = "refresh_token";

    @Value("${security.app.jwt-private-key}")
    private Resource privateKeyPem;

    @Value("${security.app.jwt-public-key}")
    private Resource publicKeyPem;

    @Value("${security.app.jwt-access-expiration-ms}")
    private long jwtAccessExpirationMs;

    @Value("${security.app.jwt-refresh-expiration-ms}")
    private long jwtRefreshExpirationMs;

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;

    @PostConstruct
    void init() throws Exception {
        privateKey = loadPrivateKey(privateKeyPem.getContentAsString(StandardCharsets.UTF_8));
        publicKey = loadPublicKey(publicKeyPem.getContentAsString(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UUID userId, List<String> roles) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("token_type", ACCESS_TOKEN_TYPE)
                .claim("user_id", userId.toString())
                .claim("roles", roles)
                .jwtID(randomJti())
                .issueTime(new Date())
                .expirationTime(expTime(jwtAccessExpirationMs))
                .build();
        return signClaims(claims);
    }

    public String generateRefreshToken(UUID userId) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("token_type", REFRESH_TOKEN_TYPE)
                .claim("user_id", userId.toString())
                .jwtID(randomJti())
                .issueTime(new Date())
                .expirationTime(expTime(jwtRefreshExpirationMs))
                .build();
        return signClaims(claims);
    }

    /** Ném JwtException nếu chữ ký sai, hết hạn, hoặc không đúng loại token mong đợi. */
    public DecodedToken decodeAndValidate(String token, String expectedType) {
        JWTClaimsSet claims = parseAndValidate(token);

        String type;
        UUID userId;
        try {
            type = claims.getStringClaim("token_type");
            userId = UUID.fromString(claims.getStringClaim("user_id"));
        } catch (Exception exception) {
            throw new JwtException("Token claims malformed", exception);
        }

        if (!expectedType.equals(type)) {
            throw new JwtException("Expected token_type=" + expectedType + " but was " + type);
        }

        return new DecodedToken(type, userId, claims.getJWTID(), claims.getExpirationTime(), extractRoles(claims));
    }

    private List<String> extractRoles(JWTClaimsSet claims) {
        try {
            return claims.getStringListClaim("roles");
        } catch (Exception e) {
            return null; // refresh token không có roles
        }
    }

    private Date expTime(long ms) {
        return new Date(System.currentTimeMillis() + ms);
    }

    private JWTClaimsSet parseAndValidate(String token) {
        JWTClaimsSet claims = parse(token);

        Date exp = claims.getExpirationTime();
        if (exp == null || exp.before(new Date())) {
            throw new JwtException("Token expired");
        }

        return claims;
    }

    private JWTClaimsSet parse(String token) {
        try {
            JWSObject jws = JWSObject.parse(token);

            boolean ok = jws.verify(new RSASSAVerifier(publicKey));
            if (!ok) {
                throw new JwtException("Signature invalid");
            }

            return JWTClaimsSet.parse(jws.getPayload().toJSONObject());
        } catch (JwtException exception) {
            throw exception;
        } catch (Exception e) {
            throw new JwtException("Token parse/verify failed", e);
        }
    }

    private String signClaims(JWTClaimsSet claims) {
        try {
            JWSObject jws = new JWSObject(
                    new JWSHeader(JWSAlgorithm.RS256),
                    new Payload(claims.toJSONObject())
            );
            jws.sign(new RSASSASigner(privateKey));
            return jws.serialize();
        } catch (Exception e) {
            throw new JwtException("Token signing failed", e);
        }
    }

    private String randomJti() {
        return UUID.randomUUID().toString();
    }

    private static RSAPrivateKey loadPrivateKey(String pem) throws Exception {
        byte[] der = Base64.getDecoder().decode(pem
                .replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "")
                .replaceAll("\\s", ""));
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static RSAPublicKey loadPublicKey(String pem) throws Exception {
        byte[] der = Base64.getDecoder().decode(pem
                .replaceAll("-----(BEGIN|END) PUBLIC KEY-----", "")
                .replaceAll("\\s", ""));
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(der));
    }

    public record DecodedToken(
            String type,
            UUID userId,
            String jti,
            Date exp,
            List<String> roles
    ) {}
}
