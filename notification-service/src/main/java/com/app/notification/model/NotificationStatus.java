package com.app.notification.model;

public enum NotificationStatus {
    /** Chưa ai claim. Sweeper sẽ nhặt. */
    PENDING,
    /** Đang giữ lease để gửi. Lease hết hạn mà vẫn PROCESSING thì bị nhặt lại. */
    PROCESSING,
    SENT,
    /** Đã thử hết số lần cho phép, không gửi được. Sweeper bỏ qua từ đây. */
    FAILED
}
