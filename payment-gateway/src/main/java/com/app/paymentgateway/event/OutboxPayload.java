package com.app.paymentgateway.event;

import java.util.UUID;

public interface OutboxPayload {

    UUID messageId();
}
