package com.app.paymentgateway.provider;

import com.app.paymentgateway.config.VnpayProperties;
import com.app.paymentgateway.entity.Payment;
import com.app.paymentgateway.exception.VnpayCallbackException;
import com.app.paymentgateway.model.PaymentProviderType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "VNPAY")
public class VnpayPaymentProvider implements PaymentProvider {

    private static final ZoneId VIETNAM_TIME = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNPAY_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final VnpayProperties properties;
    private final VnpaySigner signer;
    private final RestClient restClient;

    public VnpayPaymentProvider(
            VnpayProperties properties,
            VnpaySigner signer,
            RestClient.Builder restClientBuilder
    ) {
        this.properties = properties;
        this.signer = signer;
        this.restClient = restClientBuilder
                .baseUrl(properties.apiUrl())
                .build();
    }

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

    @Override
    public PaymentRefundResult refund(Payment payment, UUID requestId) {
        Map<String, String> request = refundRequest(payment, requestId);
        request.put("vnp_SecureHash", signer.signData(refundHashData(request)));

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri("")
                .body(request)
                .retrieve()
                .body(Map.class);
        if (response == null) {
            return PaymentRefundResult.retryable("VNPAY returned an empty refund response");
        }

        Map<String, String> fields = response.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue() == null
                                ? ""
                                : entry.getValue().toString()
                ));
        if (!signer.verifyData(
                refundResponseHashData(fields),
                fields.get("vnp_SecureHash")
        )) {
            return PaymentRefundResult.rejected("Invalid VNPAY refund signature");
        }

        String responseCode = fields.get("vnp_ResponseCode");
        String transactionStatus = fields.get("vnp_TransactionStatus");
        if ("00".equals(responseCode) && "00".equals(transactionStatus)) {
            return PaymentRefundResult.success();
        }
        if ("94".equals(responseCode)
                || "99".equals(responseCode)
                || "05".equals(transactionStatus)
                || "06".equals(transactionStatus)) {
            return PaymentRefundResult.retryable(
                    "VNPAY refund is still pending: " + responseCode
            );
        }
        return PaymentRefundResult.rejected(
                "VNPAY rejected refund: " + responseCode
        );
    }

    private String toVnpayAmount(BigDecimal amount) {
        return amount.movePointRight(2)
                .setScale(0, RoundingMode.UNNECESSARY)
                .toPlainString();
    }

    private Map<String, String> refundRequest(
            Payment payment,
            UUID requestId
    ) {
        String createdAt = VNPAY_TIME.format(
                payment.getCreatedAt().atZone(VIETNAM_TIME)
        );
        Map<String, String> request = new LinkedHashMap<>();
        request.put("vnp_RequestId", requestId.toString().replace("-", ""));
        request.put("vnp_Version", properties.version());
        request.put("vnp_Command", "refund");
        request.put("vnp_TmnCode", properties.tmnCode());
        request.put("vnp_TransactionType", "02");
        request.put("vnp_TxnRef", payment.getId().toString());
        request.put("vnp_Amount", toVnpayAmount(payment.getAmount()));
        request.put(
                "vnp_TransactionNo",
                payment.getProviderTransactionNo() == null
                        ? ""
                        : payment.getProviderTransactionNo()
        );
        request.put("vnp_TransactionDate", createdAt);
        request.put("vnp_CreateBy", "demo-ecommerce");
        request.put(
                "vnp_CreateDate",
                VNPAY_TIME.format(java.time.ZonedDateTime.now(VIETNAM_TIME))
        );
        request.put("vnp_IpAddr", "127.0.0.1");
        request.put("vnp_OrderInfo", "Hoan tien payment " + payment.getId());
        return request;
    }

    private String refundHashData(Map<String, String> request) {
        return String.join("|",
                request.get("vnp_RequestId"),
                request.get("vnp_Version"),
                request.get("vnp_Command"),
                request.get("vnp_TmnCode"),
                request.get("vnp_TransactionType"),
                request.get("vnp_TxnRef"),
                request.get("vnp_Amount"),
                request.get("vnp_TransactionNo"),
                request.get("vnp_TransactionDate"),
                request.get("vnp_CreateBy"),
                request.get("vnp_CreateDate"),
                request.get("vnp_IpAddr"),
                request.get("vnp_OrderInfo")
        );
    }

    private String refundResponseHashData(Map<String, String> response) {
        return String.join("|",
                response.getOrDefault("vnp_ResponseId", ""),
                response.getOrDefault("vnp_Command", ""),
                response.getOrDefault("vnp_ResponseCode", ""),
                response.getOrDefault("vnp_Message", ""),
                response.getOrDefault("vnp_TmnCode", ""),
                response.getOrDefault("vnp_TxnRef", ""),
                response.getOrDefault("vnp_Amount", ""),
                response.getOrDefault("vnp_BankCode", ""),
                response.getOrDefault("vnp_PayDate", ""),
                response.getOrDefault("vnp_TransactionNo", ""),
                response.getOrDefault("vnp_TransactionType", ""),
                response.getOrDefault("vnp_TransactionStatus", ""),
                response.getOrDefault("vnp_OrderInfo", "")
        );
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
