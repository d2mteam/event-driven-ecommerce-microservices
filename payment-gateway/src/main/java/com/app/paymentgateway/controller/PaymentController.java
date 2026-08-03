package com.app.paymentgateway.controller;

import com.app.paymentgateway.dto.CompleteMockPaymentRequest;
import com.app.paymentgateway.dto.CreatePaymentRequest;
import com.app.paymentgateway.dto.PaymentResponse;
import com.app.paymentgateway.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Validated
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private static final String PAYMENTS_PATH = "/api/payments";

    private final PaymentService paymentService;

    @PostMapping("/internal/payments")
    public ResponseEntity<PaymentResponse> create(
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        PaymentResponse response = paymentService.create(request);
        return ResponseEntity
                .created(URI.create(PAYMENTS_PATH + "/" + response.id()))
                .body(response);
    }

    @GetMapping(PAYMENTS_PATH + "/{id}")
    public PaymentResponse findById(@PathVariable @Positive Long id) {
        return paymentService.findById(id);
    }

    @PostMapping(PAYMENTS_PATH + "/{id}/mock")
    public PaymentResponse complete(
            @PathVariable @Positive Long id,
            @Valid @RequestBody CompleteMockPaymentRequest request
    ) {
        return paymentService.complete(id, request);
    }
}
