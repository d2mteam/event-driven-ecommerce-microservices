package com.app.order.event;

import java.util.UUID;

public interface OutboxPayload {

    UUID messageId();
}
