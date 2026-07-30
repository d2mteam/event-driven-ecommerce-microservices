import http from 'k6/http';
import { check } from 'k6';

export const orderUrl =
    __ENV.ORDER_URL || 'http://localhost:8080/api/orders';

export const userId =
    __ENV.USER_ID || '11111111-1111-1111-1111-111111111111';

export const happyProductId = envNumber('HAPPY_PRODUCT_ID', 12);
export const secondProductId = envNumber('SECOND_PRODUCT_ID', 10);
export const missingProductId = envNumber('MISSING_PRODUCT_ID', 999999);
export const outOfStockProductId =
    envNumber('OUT_OF_STOCK_PRODUCT_ID', 1);
export const outOfStockQuantity =
    envNumber('OUT_OF_STOCK_QUANTITY', 2147483647);

export const jsonHeaders = {
    'Content-Type': 'application/json',
};

export const authenticatedHeaders = {
    ...jsonHeaders,
    'X-User-Id': userId,
};

export const smokeOptions = {
    vus: 1,
    iterations: 1,
    thresholds: {
        checks: ['rate==1'],
        http_req_failed: ['rate==0'],
        http_req_duration: [
            `p(95)<${envNumber('MAX_P95_MS', 3000)}`,
        ],
    },
};

http.setResponseCallback(http.expectedStatuses(201, 404, 409, 422));

export function postOrder(name, body, headers = authenticatedHeaders) {
    const idempotencyKey =
        `k6-${name}-${__VU}-${__ITER}-${Date.now()}-${Math.random()}`;

    return http.post(
        orderUrl,
        JSON.stringify(body),
        {
            headers: {
                'Idempotency-Key': idempotencyKey,
                ...headers,
            },
            timeout: __ENV.REQUEST_TIMEOUT || '5s',
            tags: {
                name: `POST /orders - ${name}`,
                case: name,
            },
        }
    );
}

export function validOrderBody() {
    return {
        items: [
            {
                productId: happyProductId,
                quantity: 1,
            },
        ],
    };
}

export function jsonBody(response) {
    try {
        return response.json();
    } catch (_) {
        return null;
    }
}

export function checkApiError(name, response, expectedStatus) {
    const body = jsonBody(response);

    check(response, {
        [`${name}: HTTP ${expectedStatus}`]: (result) =>
            result.status === expectedStatus,
        [`${name}: valid error body`]: () =>
            body !== null
            && body.status === expectedStatus
            && body.path === '/api/orders',
    });

    return body;
}

function envNumber(name, defaultValue) {
    return Number(__ENV[name] || defaultValue);
}
