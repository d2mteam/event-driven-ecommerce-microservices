package com.app.notification.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Chỉ biết gửi một email; retry và DLT do Kafka listener xử lý. */
@Component
@RequiredArgsConstructor
public class EmailNotifier {

    private static final String SUBJECT = "Cập nhật đơn hàng";
    private static final String FROM = "notification-service@demo.local";

    private final JavaMailSender mailSender;

    public void send(String toEmail, String message) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setFrom(FROM);
        mailMessage.setTo(toEmail);
        mailMessage.setSubject(SUBJECT);
        mailMessage.setText(message);
        mailSender.send(mailMessage);
    }
}
