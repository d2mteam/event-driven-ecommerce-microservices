package com.app.paymentgateway.provider;

import com.app.paymentgateway.config.VnpayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class VnpaySigner {

    private static final String HMAC_SHA_512 = "HmacSHA512";

    private final VnpayProperties properties;

    public String sign(Map<String, String> parameters) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_512);
            mac.init(new SecretKeySpec(
                    properties.hashSecret().getBytes(StandardCharsets.UTF_8),
                    HMAC_SHA_512
            ));
            return HexFormat.of().formatHex(
                    mac.doFinal(canonicalize(parameters).getBytes(StandardCharsets.UTF_8))
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot sign VNPAY request", exception);
        }
    }

    public boolean verify(Map<String, String> parameters, String secureHash) {
        if (secureHash == null || secureHash.isBlank()) {
            return false;
        }
        try {
            byte[] expected = HexFormat.of().parseHex(sign(parameters));
            byte[] received = HexFormat.of().parseHex(secureHash);
            return MessageDigest.isEqual(expected, received);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public String canonicalize(Map<String, String> parameters) {
        return parameters.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("vnp_"))
                .filter(entry -> !entry.getKey().equals("vnp_SecureHash"))
                .filter(entry -> !entry.getKey().equals("vnp_SecureHashType"))
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }
}
