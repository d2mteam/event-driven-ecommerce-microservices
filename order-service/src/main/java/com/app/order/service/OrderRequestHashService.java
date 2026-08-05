package com.app.order.service;

import com.app.order.client.InventoryReservationItemRequest;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
public class OrderRequestHashService {

    public String hash(List<InventoryReservationItemRequest> items) {
        MessageDigest digest = sha256();
        for (InventoryReservationItemRequest item : items) {
            digest.update(item.productId().toString()
                    .getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            digest.update(item.quantity().toString()
                    .getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
