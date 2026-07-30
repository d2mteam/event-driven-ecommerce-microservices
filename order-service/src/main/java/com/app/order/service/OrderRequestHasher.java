package com.app.order.service;

import com.app.order.client.InventoryReservationItemRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Component
public class OrderRequestHasher {

    public String hash(List<InventoryReservationItemRequest> items) {
        StringBuilder canonicalRequest = new StringBuilder();
        for (InventoryReservationItemRequest item : items) {
            canonicalRequest
                    .append(item.productId())
                    .append(':')
                    .append(item.quantity())
                    .append(';');
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(
                            canonicalRequest.toString()
                                    .getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
