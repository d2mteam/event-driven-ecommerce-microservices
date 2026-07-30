import http from 'k6/http';
import { check, sleep } from 'k6';
import execution from 'k6/execution';

import {
    authenticatedHeaders,
    jsonBody,
    postOrder,
} from './common.js';
import {
    capacityOptions,
    productCount,
    thinkTimeSeconds,
} from './capacity-config.js';

export const options = capacityOptions;

http.setResponseCallback(http.expectedStatuses(201));

export default function () {
    const productId =
        (execution.scenario.iterationInTest % productCount) + 1;
    const response = postOrder(
        'capacity',
        {
            items: [
                {
                    productId,
                    quantity: 1,
                },
            ],
        },
        authenticatedHeaders
    );
    const order = jsonBody(response);

    check(response, {
        'capacity: HTTP 201': (result) => result.status === 201,
        'capacity: order confirmed': () =>
            order !== null && order.status === 'CONFIRMED',
    });

    if (thinkTimeSeconds > 0) {
        sleep(thinkTimeSeconds);
    }
}
