package com.example.ecommerce.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import com.example.ecommerce.entity.Payment;
import com.example.ecommerce.entity.PurchaseOrder;
import com.example.ecommerce.repository.PaymentRepository;

@Service
public class CashOnDeliveryPaymentService implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final String currency;

    public CashOnDeliveryPaymentService(PaymentRepository paymentRepository,
                                        @Value("${app.store.currency:USD}") String currency) {
        this.paymentRepository = paymentRepository;
        try {
            this.currency = java.util.Currency.getInstance(currency).getCurrencyCode();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("STORE_CURRENCY must be a valid ISO 4217 currency code", exception);
        }
    }

    @Override
    @Transactional
    public Payment initializePayment(PurchaseOrder order) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setMethod("CASH_ON_DELIVERY");
        payment.setStatus("PENDING");
        payment.setAmount(order.getTotal());
        payment.setCurrency(currency);
        return paymentRepository.save(payment);
    }
}
