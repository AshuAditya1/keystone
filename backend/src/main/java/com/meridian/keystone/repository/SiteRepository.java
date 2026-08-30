package com.meridian.keystone.repository;

import com.meridian.keystone.domain.Site;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

public interface SiteRepository
        extends JpaRepository<Site, Long>, JpaSpecificationExecutor<Site> {

    /** Paged search with the owning customer fetched (avoids N+1 on the list). */
    @Override
    @EntityGraph(attributePaths = {"customer"})
    Page<Site> findAll(Specification<Site> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"customer"})
    Optional<Site> findWithCustomerById(Long id);

    @EntityGraph(attributePaths = {"customer"})
    List<Site> findByCustomerIdOrderByNameAsc(Long customerId);

    boolean existsByCustomerIdAndNameIgnoreCase(Long customerId, String name);

    long countByCustomerId(Long customerId);
}
