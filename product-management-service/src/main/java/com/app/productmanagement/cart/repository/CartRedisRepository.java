package com.app.productmanagement.cart.repository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Repository
public class CartRedisRepository {

    private static final String KEY_PREFIX = "cart:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public CartRedisRepository(
            StringRedisTemplate redisTemplate,
            @Value("${app.cart.ttl}") Duration ttl
    ) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    public Map<Long, Integer> findItems(UUID userId) {
        Map<Object, Object> storedItems = redisTemplate.opsForHash()
                .entries(key(userId));
        Map<Long, Integer> items = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : storedItems.entrySet()) {
            items.put(
                    Long.valueOf(entry.getKey().toString()),
                    Integer.valueOf(entry.getValue().toString())
            );
        }
        return items;
    }

    public void putItem(UUID userId, Long productId, int quantity) {
        String cartKey = key(userId);
        redisTemplate.opsForHash().put(
                cartKey,
                productId.toString(),
                Integer.toString(quantity)
        );
        redisTemplate.expire(cartKey, ttl);
    }

    public void deleteItem(UUID userId, Long productId) {
        redisTemplate.opsForHash().delete(key(userId), productId.toString());
    }

    private String key(UUID userId) {
        return KEY_PREFIX + userId;
    }
}
