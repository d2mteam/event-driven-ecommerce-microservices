import { check, group } from 'k6';

import {
    authenticatedHeaders,
    happyProductId,
    jsonBody,
    postOrder,
    secondProductId,
    smokeOptions,
} from './common.js';

export const options = smokeOptions;

export default function () {
    group('Place order with one product', placeSingleItemOrder);
    group('Place order with multiple products', placeMultipleItemOrder);
}

function placeSingleItemOrder() {
    const response = postOrder(
        'happy_single_item',
        {
            items: [
                {
                    productId: happyProductId,
                    quantity: 1,
                },
            ],
        },
        authenticatedHeaders
    );
    const order = jsonBody(response);

    check(response, {
        'single item: HTTP 201': (result) => result.status === 201,
        'single item: order confirmed': () =>
            order !== null && order.status === 'CONFIRMED',
        'single item: reservation created': () =>
            order !== null && Number(order.reservationId) > 0,
        'single item: correct product': () =>
            order !== null
            && Array.isArray(order.items)
            && order.items.length === 1
            && order.items[0].productId === happyProductId,
    });
}

function placeMultipleItemOrder() {
    const response = postOrder(
        'happy_multiple_items',
        {
            items: [
                {
                    productId: happyProductId,
                    quantity: 1,
                },
                {
                    productId: secondProductId,
                    quantity: 1,
                },
                {
                    productId: happyProductId,
                    quantity: 1,
                },
            ],
        },
        authenticatedHeaders
    );
    const order = jsonBody(response);
    const duplicatedProduct = order === null || !Array.isArray(order.items)
        ? null
        : order.items.find(
            (item) => item.productId === happyProductId
        );

    check(response, {
        'multiple items: HTTP 201': (result) => result.status === 201,
        'multiple items: order confirmed': () =>
            order !== null && order.status === 'CONFIRMED',
        'multiple items: duplicate lines merged': () =>
            order !== null
            && Array.isArray(order.items)
            && order.items.length === 2
            && duplicatedProduct !== null
            && duplicatedProduct !== undefined
            && duplicatedProduct.quantity === 2,
        'multiple items: total price calculated': () =>
            order !== null && Number(order.totalPrice) > 0,
    });
}
