package com.app.productmanagement.cart;

import com.app.productmanagement.cart.dto.CartItemResponse;
import com.app.productmanagement.cart.dto.CartResponse;
import com.app.productmanagement.cart.mapper.CartMapper;
import com.app.productmanagement.cart.repository.CartRedisRepository;
import com.app.productmanagement.entity.Product;
import com.app.productmanagement.model.ProductStatus;
import com.app.productmanagement.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final UUID USER_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111"
    );

    @Mock
    private CartRedisRepository cartRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CartMapper cartMapper;

    @InjectMocks
    private CartService cartService;

    @Test
    void returnsCartItemsWithCurrentProductData() {
        Product keyboard = product(2L, "Keyboard", "450000");
        Product mouse = product(1L, "Mouse", "250000");
        when(cartRepository.findItems(USER_ID)).thenReturn(Map.of(2L, 1, 1L, 2));
        when(productRepository.findAllByIdInAndStatus(
                List.of(1L, 2L),
                ProductStatus.ACTIVE
        )).thenReturn(List.of(keyboard, mouse));

        CartItemResponse mouseResponse = new CartItemResponse(
                1L,
                "Mouse",
                "Electronics",
                new BigDecimal("250000"),
                2
        );
        CartItemResponse keyboardResponse = new CartItemResponse(
                2L,
                "Keyboard",
                "Electronics",
                new BigDecimal("450000"),
                1
        );
        when(cartMapper.toResponse(mouse, 2)).thenReturn(mouseResponse);
        when(cartMapper.toResponse(keyboard, 1)).thenReturn(keyboardResponse);

        CartResponse response = cartService.getCart(USER_ID);

        assertThat(response.items()).containsExactly(mouseResponse, keyboardResponse);
    }

    @Test
    void putsItemAfterCheckingThatProductIsActive() {
        Product mouse = product(1L, "Mouse", "250000");
        when(productRepository.findByIdAndStatus(1L, ProductStatus.ACTIVE))
                .thenReturn(java.util.Optional.of(mouse));
        CartItemResponse expected = new CartItemResponse(
                1L,
                "Mouse",
                "Electronics",
                new BigDecimal("250000"),
                3
        );
        when(cartMapper.toResponse(mouse, 3)).thenReturn(expected);

        CartItemResponse response = cartService.putItem(USER_ID, 1L, 3);

        assertThat(response).isEqualTo(expected);
        verify(cartRepository).putItem(USER_ID, 1L, 3);
    }

    @Test
    void deletesItemFromCart() {
        cartService.deleteItem(USER_ID, 1L);

        verify(cartRepository).deleteItem(USER_ID, 1L);
    }

    private Product product(Long id, String name, String price) {
        return Product.builder()
                .id(id)
                .name(name)
                .category("Electronics")
                .price(new BigDecimal(price))
                .status(ProductStatus.ACTIVE)
                .build();
    }
}
