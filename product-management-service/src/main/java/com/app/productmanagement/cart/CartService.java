package com.app.productmanagement.cart;

import com.app.productmanagement.cart.dto.CartItemResponse;
import com.app.productmanagement.cart.dto.CartResponse;
import com.app.productmanagement.cart.mapper.CartMapper;
import com.app.productmanagement.cart.repository.CartRedisRepository;
import com.app.productmanagement.entity.Product;
import com.app.productmanagement.exception.ProductNotFoundException;
import com.app.productmanagement.model.ProductStatus;
import com.app.productmanagement.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRedisRepository cartRepository;
    private final ProductRepository productRepository;
    private final CartMapper cartMapper;

    public CartResponse getCart(UUID userId) {
        Map<Long, Integer> quantities = new TreeMap<>(
                cartRepository.findItems(userId)
        );
        if (quantities.isEmpty()) {
            return new CartResponse(List.of());
        }

        List<Long> productIds = new ArrayList<>(quantities.keySet());
        List<Product> products = productRepository.findAllByIdInAndStatus(
                productIds,
                ProductStatus.ACTIVE
        );
        Map<Long, Product> productsById = new HashMap<>();
        for (Product product : products) {
            productsById.put(product.getId(), product);
        }

        List<CartItemResponse> items = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Product product = productsById.get(entry.getKey());
            if (product != null) {
                items.add(cartMapper.toResponse(product, entry.getValue()));
            }
        }
        return new CartResponse(List.copyOf(items));
    }

    public CartItemResponse putItem(
            UUID userId,
            Long productId,
            int quantity
    ) {
        Product product = productRepository
                .findByIdAndStatus(productId, ProductStatus.ACTIVE)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        cartRepository.putItem(userId, productId, quantity);
        return cartMapper.toResponse(product, quantity);
    }

    public void deleteItem(UUID userId, Long productId) {
        cartRepository.deleteItem(userId, productId);
    }
}
