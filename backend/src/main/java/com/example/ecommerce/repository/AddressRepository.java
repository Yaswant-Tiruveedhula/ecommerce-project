package com.example.ecommerce.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.ecommerce.entity.Address;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findAllByUserIdOrderByDefaultAddressDescIdDesc(Long userId);

    Optional<Address> findByIdAndUserId(Long id, Long userId);
}
