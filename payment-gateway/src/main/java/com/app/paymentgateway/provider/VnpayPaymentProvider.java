package com.app.paymentgateway.provider;

import com.app.paymentgateway.config.VnpayProperties;
import com.app.paymentgateway.entity.Payment;
import com.app.paymentgateway.exception.VnpayCallbackException;
import com.app.paymentgateway.model.PaymentProviderType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "VNPAY")
public class VnpayPaymentProvider implements PaymentProvider {

    private static final ZoneId VIETNAM_TIME = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNPAY_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final VnpayProperties properties;
    private final VnpaySigner signer;

    @Override
    public PaymentProviderType type() {
        return PaymentProviderType.VNPAY;
    }

    @Override
    public String createPaymentUrl(Payment payment, String clientIp) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("vnp_Version", properties.version());
        parameters.put("vnp_Command", "pay");
        parameters.put("vnp_TmnCode", properties.tmnCode());
        parameters.put("vnp_Amount", toVnpayAmount(payment.getAmount()));
        parameters.put("vnp_CurrCode", "VND");
        parameters.put("vnp_TxnRef", payment.getId().toString());
        parameters.put(
                "vnp_OrderInfo",
                "Thanh toan don hang "
                        + payment.getOrderId().toString().replace("-", "")
        );
        parameters.put("vnp_OrderType", properties.orderType());
        parameters.put("vnp_Locale", properties.locale());
        parameters.put("vnp_ReturnUrl", properties.returnUrl());
        parameters.put("vnp_IpAddr", clientIp);
        parameters.put(
                "vnp_CreateDate",
                VNPAY_TIME.format(payment.getCreatedAt().atZone(VIETNAM_TIME))
        );
        parameters.put(
                "vnp_ExpireDate",
                VNPAY_TIME.format(payment.getExpiresAt().atZone(VIETNAM_TIME))
        );

        String query = signer.canonicalize(parameters);
        return properties.payUrl() + "?" + query
                + "&vnp_SecureHash=" + signer.sign(parameters);
    }

    public VnpayNotification verifyNotification(Map<String, String> parameters) {
        String secureHash = parameters.get("vnp_SecureHash");
        if (!signer.verify(parameters, secureHash)
                || !properties.tmnCode().equals(parameters.get("vnp_TmnCode"))) {
            throw new VnpayCallbackException("97", "Invalid VNPAY signature");
        }

        try {
            return new VnpayNotification(
                    Long.valueOf(required(parameters, "vnp_TxnRef")),
                    Long.parseLong(required(parameters, "vnp_Amount")),
                    parameters.get("vnp_TransactionNo"),
                    required(parameters, "vnp_ResponseCode"),
                    required(parameters, "vnp_TransactionStatus")
            );
        } catch (NumberFormatException exception) {
            throw new VnpayCallbackException(
                    "99",
                    "Invalid numeric VNPAY callback field",
                    exception
            );
        }
    }

    public String frontendBaseUrl() {
        return properties.frontendBaseUrl().replaceAll("/+$", "");
    }

    private String toVnpayAmount(BigDecimal amount) {
        return amount.movePointRight(2)
                .setScale(0, RoundingMode.UNNECESSARY)
                .toPlainString();
    }

    private String required(Map<String, String> parameters, String name) {
        String value = parameters.get(name);
        if (value == null || value.isBlank()) {
            throw new VnpayCallbackException(
                    "99",
                    "Missing VNPAY callback field: " + name
            );
        }
        return value;
    }
}
