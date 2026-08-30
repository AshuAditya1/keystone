package com.meridian.keystone.repository;

import com.meridian.keystone.domain.Role;
import com.meridian.keystone.domain.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Data access for {@link User}. Spring Data derives the query implementations.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** Assignment pickers need technicians ordered by name. */
    List<User> findByRoleOrderByFullNameAsc(Role role);

    List<User> findByRoleInOrderByFullNameAsc(List<Role> roles);

    @EntityGraph(attributePaths = {"customer"})
    List<User> findAllByOrderByFullNameAsc();

    boolean existsByCustomerId(Long customerId);

    /** Recipients for operational alerts: managers and dispatchers. */
    List<User> findByRoleInAndActiveTrue(List<Role> roles);
}
