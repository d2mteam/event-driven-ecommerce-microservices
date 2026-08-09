package com.app.productmanagement.cart;

import com.app.productmanagement.cart.dto.CartItemResponse;
import com.app.productmanagement.cart.dto.CartResponse;
import com.app.productmanagement.cart.dto.UpdateCartItemRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final CartService cartService;

    @GetMapping
    public CartResponse getCart(
            @RequestHeader(USER_ID_HEADER) UUID userId
    ) {
        return cartService.getCart(userId);
    }

    @PutMapping("/items/{productId}")
    public CartItemResponse putItem(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @PathVariable Long productId,
            @Valid @RequestBody UpdateCartItemRequest request
    ) {
        return cartService.putItem(userId, productId, request.quantity());
    }

    @DeleteMapping("/items/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @PathVariable Long productId
    ) {
        cartService.deleteItem(userId, productId);
    }
}
