package com.example.ecommerce.service;

import com.example.ecommerce.entity.Payment;
import com.example.ecommerce.entity.PurchaseOrder;

public interface PaymentService {
    Payment initializePayment(PurchaseOrder order);
}
