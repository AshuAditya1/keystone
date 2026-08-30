package com.meridian.keystone.dto;

import com.meridian.keystone.domain.Site;

public record SiteView(
        Long id,
        Long customerId,
        String customerName,
        String name,
        String address) {

    public static SiteView from(Site site) {
        return new SiteView(
                site.getId(),
                site.getCustomer().getId(),
                site.getCustomer().getName(),
                site.getName(),
                site.getAddress());
    }
}
