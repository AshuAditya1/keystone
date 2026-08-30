package com.meridian.keystone.repository;

import com.meridian.keystone.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface CustomerRepository
        extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    boolean existsByNameIgnoreCase(String name);

    Optional<Customer> findByNameIgnoreCase(String name);
}
