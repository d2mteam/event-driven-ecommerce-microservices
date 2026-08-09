package com.app.productmanagement.cart.dto;

import java.util.List;

public record CartResponse(List<CartItemResponse> items) {
}
