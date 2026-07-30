import { check } from 'k6';

import {
    authenticatedHeaders,
    checkApiError,
    happyProductId,
    jsonHeaders,
    missingProductId,
    outOfStockProductId,
    outOfStockQuantity,
    postOrder,
    smokeOptions,
    validOrderBody,
} from './common.js';

export const options = smokeOptions;

export default function () {
    missingUserHeader();
    malformedUserHeader();
    emptyItems();
    invalidQuantity();
    productNotFound();
    outOfStock();
}

function missingUserHeader() {
    const response = postOrder(
        'missing_user_header',
        validOrderBody(),
        jsonHeaders
    );

    checkApiError('missing user header', response, 422);
}

function malformedUserHeader() {
    const response = postOrder(
        'malformed_user_header',
        validOrderBody(),
        {
            ...jsonHeaders,
            'X-User-Id': 'not-a-uuid',
        }
    );

    checkApiError('malformed user header', response, 422);
}

function emptyItems() {
    const response = postOrder(
        'empty_items',
        { items: [] },
        authenticatedHeaders
    );

    checkApiError('empty items', response, 422);
}

function invalidQuantity() {
    const response = postOrder(
        'invalid_quantity',
        {
            items: [
                {
                    productId: happyProductId,
                    quantity: 0,
                },
            ],
        },
        authenticatedHeaders
    );

    checkApiError('invalid quantity', response, 422);
}

function productNotFound() {
    const response = postOrder(
        'product_not_found',
        {
            items: [
                {
                    productId: missingProductId,
                    quantity: 1,
                },
            ],
        },
        authenticatedHeaders
    );
    const body = checkApiError('product not found', response, 404);

    check(body, {
        'product not found: response contains product id': (result) =>
            result !== null
            && result.message.includes(String(missingProductId)),
    });
}

function outOfStock() {
    const response = postOrder(
        'out_of_stock',
        {
            items: [
                {
                    productId: outOfStockProductId,
                    quantity: outOfStockQuantity,
                },
            ],
        },
        authenticatedHeaders
    );
    const body = checkApiError('out of stock', response, 409);

    check(body, {
        'out of stock: response contains business message': (result) =>
            result !== null
            && result.message.toLowerCase().includes('out of stock'),
    });
}
