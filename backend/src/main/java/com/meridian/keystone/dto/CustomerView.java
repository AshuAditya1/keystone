package com.meridian.keystone.dto;

import com.meridian.keystone.domain.Customer;

public record CustomerView(
        Long id,
        String name,
        String contactEmail,
        String contactPhone) {

    public static CustomerView from(Customer customer) {
        return new CustomerView(
                customer.getId(),
                customer.getName(),
                customer.getContactEmail(),
                customer.getContactPhone());
    }
}
