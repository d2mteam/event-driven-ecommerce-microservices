package com.app.paymentgateway.controller;

import com.app.paymentgateway.dto.VnpayIpnResponse;
import com.app.paymentgateway.exception.PaymentNotFoundException;
import com.app.paymentgateway.exception.VnpayCallbackException;
import com.app.paymentgateway.provider.VnpayNotification;
import com.app.paymentgateway.provider.VnpayPaymentProvider;
import com.app.paymentgateway.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments/vnpay")
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "VNPAY")
public class VnpayCallbackController {

    private final VnpayPaymentProvider paymentProvider;
    private final PaymentService paymentService;

    @GetMapping("/ipn")
    public VnpayIpnResponse ipn(@RequestParam Map<String, String> parameters) {
        try {
            return paymentService.applyVnpayResult(
                    paymentProvider.verifyNotification(parameters)
            );
        } catch (VnpayCallbackException exception) {
            return new VnpayIpnResponse(
                    exception.getResponseCode(),
                    exception.getMessage()
            );
        } catch (RuntimeException exception) {
            log.error("Cannot process VNPAY IPN", exception);
            return new VnpayIpnResponse("99", "Unknown error");
        }
    }

    @GetMapping("/return")
    public ResponseEntity<Void> paymentReturn(
            @RequestParam Map<String, String> parameters
    ) {
        try {
            VnpayNotification notification =
                    paymentProvider.verifyNotification(parameters);
            UUID orderId = paymentService.findOrderId(notification.paymentId());
            return redirect(orderId, notification.paymentId(),
                    notification.successful() ? "success" : "failed");
        } catch (VnpayCallbackException exception) {
            return redirectToOrders("invalid");
        } catch (PaymentNotFoundException exception) {
            return redirectToOrders("unknown");
        }
    }

    private ResponseEntity<Void> redirect(
            UUID orderId,
            Long paymentId,
            String result
    ) {
        URI location = UriComponentsBuilder
                .fromUriString(paymentProvider.frontendBaseUrl())
                .path("/orders/{orderId}")
                .queryParam("paymentId", paymentId)
                .queryParam("vnpayResult", result)
                .buildAndExpand(orderId)
                .toUri();
        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, location.toString())
                .build();
    }

    private ResponseEntity<Void> redirectToOrders(String result) {
        URI location = UriComponentsBuilder
                .fromUriString(paymentProvider.frontendBaseUrl())
                .path("/orders")
                .queryParam("vnpayResult", result)
                .build()
                .toUri();
        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, location.toString())
                .build();
    }
}
