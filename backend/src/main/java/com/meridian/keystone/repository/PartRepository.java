package com.meridian.keystone.repository;

import com.meridian.keystone.domain.Part;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PartRepository
        extends JpaRepository<Part, Long>, JpaSpecificationExecutor<Part> {

    boolean existsBySkuIgnoreCase(String sku);

    Optional<Part> findBySkuIgnoreCase(String sku);

    /**
     * Row-locking read used when consuming stock. Two technicians logging the
     * last unit of the same part at the same moment serialise here, so the
     * "stock can never go negative" invariant holds under concurrency rather
     * than only in the happy path.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Part p where p.id = :id")
    Optional<Part> findByIdForUpdate(@Param("id") Long id);
}
