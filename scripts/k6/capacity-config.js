function envNumber(name, defaultValue) {
    const value = Number(__ENV[name] || defaultValue);

    if (!Number.isFinite(value) || value < 0) {
        throw new Error(`${name} must be a non-negative number`);
    }

    return value;
}

function envPositiveInteger(name, defaultValue) {
    const value = envNumber(name, defaultValue);

    if (!Number.isInteger(value) || value < 1) {
        throw new Error(`${name} must be a positive integer`);
    }

    return value;
}

const rampDuration = __ENV.RAMP_DURATION || '10s';
const holdDuration = __ENV.HOLD_DURATION || '20s';
const abortDelay = __ENV.ABORT_DELAY || '30s';
const maxP95Milliseconds = envNumber('MAX_P95_MS', 1000);

const vusLevels = [
    envPositiveInteger('VUS_1', 10),
    envPositiveInteger('VUS_2', 25),
    envPositiveInteger('VUS_3', 50),
    envPositiveInteger('VUS_4', 100),
    envPositiveInteger('VUS_5', 200),
];

export const productCount = envPositiveInteger('PRODUCT_COUNT', 50);
export const thinkTimeSeconds = envNumber('THINK_TIME_SECONDS', 0);

export const capacityOptions = {
    scenarios: {
        order_capacity: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: vusLevels.flatMap((vus) => [
                { duration: rampDuration, target: vus },
                { duration: holdDuration, target: vus },
            ]).concat([
                { duration: rampDuration, target: 0 },
            ]),
            gracefulRampDown: '5s',
        },
    },
    thresholds: {
        checks: [
            {
                threshold: 'rate>=0.99',
                abortOnFail: true,
                delayAbortEval: abortDelay,
            },
        ],
        http_req_failed: [
            {
                threshold: 'rate<0.01',
                abortOnFail: true,
                delayAbortEval: abortDelay,
            },
        ],
        http_req_duration: [
            {
                threshold: `p(95)<${maxP95Milliseconds}`,
                abortOnFail: true,
                delayAbortEval: abortDelay,
            },
        ],
    },
    summaryTrendStats: [
        'avg',
        'min',
        'med',
        'p(90)',
        'p(95)',
        'p(99)',
        'max',
    ],
};
