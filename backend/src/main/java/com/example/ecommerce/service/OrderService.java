package com.example.ecommerce.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dto.ApiDtos;
import com.example.ecommerce.entity.Address;
import com.example.ecommerce.entity.Cart;
import com.example.ecommerce.entity.CartItem;
import com.example.ecommerce.entity.InventoryMovement;
import com.example.ecommerce.entity.OrderItem;
import com.example.ecommerce.entity.OrderStatus;
import com.example.ecommerce.entity.Payment;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.entity.PurchaseOrder;
import com.example.ecommerce.entity.User;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repository.AddressRepository;
import com.example.ecommerce.repository.CartItemRepository;
import com.example.ecommerce.repository.CartRepository;
import com.example.ecommerce.repository.InventoryMovementRepository;
import com.example.ecommerce.repository.PaymentRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.PurchaseOrderRepository;
import com.example.ecommerce.repository.UserRepository;

@Service
public class OrderService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private final PurchaseOrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final AddressRepository addressRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final InventoryMovementRepository movementRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    public OrderService(PurchaseOrderRepository orderRepository, CartRepository cartRepository,
                        CartItemRepository cartItemRepository, AddressRepository addressRepository,
                        ProductRepository productRepository, UserRepository userRepository,
                        InventoryMovementRepository movementRepository, PaymentRepository paymentRepository,
                        PaymentService paymentService) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.addressRepository = addressRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.movementRepository = movementRepository;
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
    }

    @Transactional
    public ApiDtos.OrderResponse checkout(Long customerId, ApiDtos.CheckoutRequest request) {
        User customer = userRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        String idempotencyKey = request.idempotencyKey().trim();
        var existing = orderRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey);
        if (existing.isPresent()) {
            return response(existing.get());
        }
        if (!"CASH_ON_DELIVERY".equals(request.paymentMethod())) {
            throw ApiException.badRequest("Only Cash on Delivery is enabled; no online payment was processed");
        }

        Cart cart = cartRepository.findByCustomerIdForUpdate(customerId)
                .orElseThrow(() -> ApiException.conflict("Shopping cart is empty"));
        List<CartItem> cartItems = cartItemRepository.findAllByCartIdOrderById(cart.getId());
        if (cartItems.isEmpty()) {
            throw ApiException.conflict("Shopping cart is empty");
        }

        Address address = addressRepository.findByIdAndUserId(request.addressId(), customerId)
                .orElseThrow(() -> ApiException.notFound("Delivery address not found"));
        List<CartItem> orderedCartItems = new ArrayList<>(cartItems);
        orderedCartItems.sort(Comparator.comparing(item -> item.getProduct().getId()));

        List<LockedLine> lines = new ArrayList<>();
        BigDecimal subtotal = ZERO;
        BigDecimal originalSubtotal = ZERO;
        for (CartItem cartItem : orderedCartItems) {
            Product product = productRepository.findByIdForUpdate(cartItem.getProduct().getId())
                    .orElseThrow(() -> ApiException.conflict("A cart product is no longer available"));
            if (!product.isActive() || !product.getCategory().isActive()) {
                throw ApiException.conflict(product.getName() + " is no longer available");
            }
            if (product.getStockQuantity() < cartItem.getQuantity()) {
                throw ApiException.conflict("Insufficient stock for " + product.getName());
            }
            BigDecimal originalLine = product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            BigDecimal unitPrice = effectivePrice(product);
            BigDecimal discountedLine = unitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            originalSubtotal = originalSubtotal.add(originalLine);
            subtotal = subtotal.add(discountedLine);
            lines.add(new LockedLine(product, cartItem.getQuantity(), unitPrice, discountedLine));
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        originalSubtotal = originalSubtotal.setScale(2, RoundingMode.HALF_UP);

        PurchaseOrder order = new PurchaseOrder();
        order.setCustomer(customer);
        order.setOrderNumber("CG-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 20).toUpperCase(Locale.ROOT));
        order.setIdempotencyKey(idempotencyKey);
        order.setStatus(OrderStatus.PENDING);
        order.setSubtotal(subtotal);
        order.setDiscountTotal(originalSubtotal.subtract(subtotal));
        order.setShippingTotal(ZERO);
        order.setTotal(subtotal);
        copyAddress(order, address);

        for (LockedLine line : lines) {
            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(line.product());
            orderItem.setProductName(line.product().getName());
            orderItem.setSku(line.product().getSku());
            orderItem.setQuantity(line.quantity());
            orderItem.setUnitPrice(line.unitPrice());
            orderItem.setLineTotal(line.lineTotal());
            order.addItem(orderItem);
        }
        order = orderRepository.saveAndFlush(order);

        for (LockedLine line : lines) {
            line.product().setStockQuantity(line.product().getStockQuantity() - line.quantity());
            recordMovement(line.product(), -line.quantity(), "ORDER", order.getId());
        }
        Payment payment = paymentService.initializePayment(order);
        cartItemRepository.deleteAllByCartId(cart.getId());
        return response(order, payment);
    }

    @Transactional(readOnly = true)
    public Page<ApiDtos.OrderResponse> customerOrders(Long customerId, int page, int size) {
        validatePage(page, size);
        return orderRepository.findAllByCustomerIdOrderByCreatedAtDesc(customerId, PageRequest.of(page, size))
                .map(this::response);
    }

    @Transactional(readOnly = true)
    public ApiDtos.OrderResponse customerOrder(Long customerId, Long orderId) {
        PurchaseOrder order = orderRepository.findByIdForUpdate(orderId)
                .filter(purchaseOrder -> purchaseOrder.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        return response(order);
    }

    @Transactional
    public ApiDtos.OrderResponse cancelByCustomer(Long customerId, Long orderId) {
        PurchaseOrder order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw ApiException.conflict("Only pending orders can be cancelled by the customer");
        }
        cancel(order);
        return response(order);
    }

    @Transactional(readOnly = true)
    public Page<ApiDtos.OrderResponse> adminOrders(int page, int size) {
        validatePage(page, size);
        return orderRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))).map(this::response);
    }

    @Transactional
    public ApiDtos.OrderResponse updateStatus(Long orderId, ApiDtos.OrderState requested) {
        PurchaseOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        OrderStatus target = OrderStatus.valueOf(requested.name());
        if (!allowedTransition(order.getStatus(), target)) {
            throw ApiException.conflict("Order status cannot transition from "
                    + order.getStatus() + " to " + target);
        }
        if (target == OrderStatus.CANCELLED) {
            cancel(order);
        } else {
            order.setStatus(target);
        }
        return response(order);
    }

    @Transactional(readOnly = true)
    public ApiDtos.DashboardResponse dashboard() {
        BigDecimal revenue = orderRepository.sumTotalByStatus(OrderStatus.DELIVERED);
        return new ApiDtos.DashboardResponse(
                userRepository.countByRolesName("CUSTOMER"),
                productRepository.count(),
                orderRepository.count(),
                revenue == null ? ZERO : revenue.setScale(2, RoundingMode.HALF_UP),
                orderRepository.countByStatus(OrderStatus.PENDING));
    }

    @Transactional(readOnly = true)
    public Page<ApiDtos.AdminCustomerResponse> customers(int page, int size) {
        validatePage(page, size);
        return userRepository.findAllByRolesName("CUSTOMER",
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(user -> new ApiDtos.AdminCustomerResponse(
                        user.getId(), user.getName(), user.getEmail(), user.isEnabled(), user.getCreatedAt()));
    }

    @Transactional
    public ApiDtos.AdminCustomerResponse setCustomerEnabled(Long userId, boolean enabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Customer not found"));
        boolean isAdmin = user.getRoles().stream().anyMatch(role -> role.getName().equals("ADMIN"));
        if (isAdmin) {
            throw ApiException.forbidden("Administrator accounts cannot be disabled through customer management");
        }
        user.setEnabled(enabled);
        return new ApiDtos.AdminCustomerResponse(
                user.getId(), user.getName(), user.getEmail(), user.isEnabled(), user.getCreatedAt());
    }

    private void cancel(PurchaseOrder order) {
        for (OrderItem item : order.getItems().stream()
                .sorted(Comparator.comparing(orderItem -> orderItem.getProduct() == null
                        ? Long.MAX_VALUE : orderItem.getProduct().getId())).toList()) {
            if (item.getProduct() == null) {
                continue;
            }
            Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                    .orElse(null);
            if (product != null) {
                try {
                    product.setStockQuantity(Math.addExact(product.getStockQuantity(), item.getQuantity()));
                } catch (ArithmeticException exception) {
                    throw ApiException.conflict("Inventory cannot be restored because stock is at its maximum");
                }
                recordMovement(product, item.getQuantity(), "ORDER_CANCEL", order.getId());
            }
        }
        order.setStatus(OrderStatus.CANCELLED);
    }

    private boolean allowedTransition(OrderStatus current, OrderStatus target) {
        return switch (current) {
            case PENDING -> target == OrderStatus.CONFIRMED || target == OrderStatus.CANCELLED;
            case CONFIRMED -> target == OrderStatus.PROCESSING || target == OrderStatus.CANCELLED;
            case PROCESSING -> target == OrderStatus.SHIPPED || target == OrderStatus.CANCELLED;
            case SHIPPED -> target == OrderStatus.DELIVERED;
            case DELIVERED, CANCELLED, REFUNDED -> false;
        };
    }

    private void recordMovement(Product product, int delta, String reason, Long orderId) {
        InventoryMovement movement = new InventoryMovement();
        movement.setProduct(product);
        movement.setQuantityDelta(delta);
        movement.setReason(reason);
        movement.setReferenceType("ORDER");
        movement.setReferenceId(orderId);
        movementRepository.save(movement);
    }

    private BigDecimal effectivePrice(Product product) {
        return product.getPrice()
                .multiply(BigDecimal.ONE.subtract(product.getDiscountPercent().movePointLeft(2)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void copyAddress(PurchaseOrder order, Address address) {
        order.setRecipientName(address.getRecipientName());
        order.setPhone(address.getPhone());
        order.setAddressLine1(address.getAddressLine1());
        order.setAddressLine2(address.getAddressLine2());
        order.setCity(address.getCity());
        order.setRegion(address.getRegion());
        order.setPostalCode(address.getPostalCode());
        order.setCountryCode(address.getCountryCode());
    }

    private ApiDtos.OrderResponse response(PurchaseOrder order) {
        Payment payment = paymentRepository.findFirstByOrderIdOrderById(order.getId()).orElse(null);
        return response(order, payment);
    }

    private ApiDtos.OrderResponse response(PurchaseOrder order, Payment payment) {
        List<ApiDtos.OrderLineResponse> items = order.getItems().stream()
                .map(item -> new ApiDtos.OrderLineResponse(item.getId(),
                        item.getProduct() == null ? null : item.getProduct().getId(),
                        item.getProductName(), item.getSku(), item.getQuantity(),
                        item.getUnitPrice(), item.getLineTotal())).toList();
        return new ApiDtos.OrderResponse(order.getId(), order.getOrderNumber(), order.getStatus().name(),
                order.getSubtotal(), order.getDiscountTotal(), order.getShippingTotal(), order.getTotal(),
                payment == null ? "CASH_ON_DELIVERY" : payment.getMethod(),
                payment == null ? "PENDING" : payment.getStatus(),
                order.getRecipientName(), order.getPhone(), order.getAddressLine1(), order.getAddressLine2(),
                order.getCity(), order.getRegion(), order.getPostalCode(), order.getCountryCode(),
                items, order.getCreatedAt());
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw ApiException.badRequest("Page must be non-negative and page size must be between 1 and 100");
        }
    }

    private record LockedLine(Product product, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }
}
