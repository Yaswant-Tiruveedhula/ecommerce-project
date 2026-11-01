package com.example.ecommerce.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dto.ApiDtos;
import com.example.ecommerce.entity.Address;
import com.example.ecommerce.entity.User;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repository.AddressRepository;
import com.example.ecommerce.repository.UserRepository;

@Service
public class AddressService {
    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    public AddressService(AddressRepository addressRepository, UserRepository userRepository) {
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.AddressResponse> list(Long customerId) {
        return addressRepository.findAllByUserIdOrderByDefaultAddressDescIdDesc(customerId).stream()
                .map(this::response).toList();
    }

    @Transactional
    public ApiDtos.AddressResponse create(Long customerId, ApiDtos.AddressRequest request) {
        User user = userRepository.findById(customerId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        List<Address> existing = addressRepository.findAllByUserIdOrderByDefaultAddressDescIdDesc(customerId);
        boolean makeDefault = request.defaultAddress() || existing.isEmpty();
        if (makeDefault) {
            existing.forEach(address -> address.setDefaultAddress(false));
        }
        Address address = new Address();
        address.setUser(user);
        apply(address, request);
        address.setDefaultAddress(makeDefault);
        return response(addressRepository.save(address));
    }

    @Transactional
    public ApiDtos.AddressResponse update(Long customerId, Long addressId, ApiDtos.AddressRequest request) {
        Address address = addressRepository.findByIdAndUserId(addressId, customerId)
                .orElseThrow(() -> ApiException.notFound("Address not found"));
        if (request.defaultAddress()) {
            addressRepository.findAllByUserIdOrderByDefaultAddressDescIdDesc(customerId)
                    .forEach(existing -> existing.setDefaultAddress(false));
        }
        apply(address, request);
        address.setDefaultAddress(request.defaultAddress());
        return response(address);
    }

    @Transactional
    public void delete(Long customerId, Long addressId) {
        Address address = addressRepository.findByIdAndUserId(addressId, customerId)
                .orElseThrow(() -> ApiException.notFound("Address not found"));
        boolean wasDefault = address.isDefaultAddress();
        addressRepository.delete(address);
        if (wasDefault) {
            addressRepository.findAllByUserIdOrderByDefaultAddressDescIdDesc(customerId).stream()
                    .findFirst()
                    .ifPresent(next -> next.setDefaultAddress(true));
        }
    }

    private void apply(Address address, ApiDtos.AddressRequest request) {
        address.setRecipientName(request.recipientName().trim());
        address.setPhone(request.phone().trim());
        address.setAddressLine1(request.addressLine1().trim());
        address.setAddressLine2(request.addressLine2() == null || request.addressLine2().isBlank()
                ? null : request.addressLine2().trim());
        address.setCity(request.city().trim());
        address.setRegion(request.region().trim());
        address.setPostalCode(request.postalCode().trim());
        address.setCountryCode(request.countryCode().trim().toUpperCase(java.util.Locale.ROOT));
        address.setUpdatedAt(Instant.now());
    }

    private ApiDtos.AddressResponse response(Address address) {
        return new ApiDtos.AddressResponse(address.getId(), address.getRecipientName(), address.getPhone(),
                address.getAddressLine1(), address.getAddressLine2(), address.getCity(), address.getRegion(),
                address.getPostalCode(), address.getCountryCode(), address.isDefaultAddress());
    }
}
