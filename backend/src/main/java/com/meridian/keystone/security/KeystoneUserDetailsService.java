package com.meridian.keystone.security;

import com.meridian.keystone.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads a user by email for authentication. The customer association is
 * fetched eagerly here (inside the transaction) so downstream code can read
 * the owning customer id without a lazy-loading exception.
 */
@Service
public class KeystoneUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public KeystoneUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .map(user -> {
                    // Touch the lazy association while the session is open.
                    if (user.getCustomer() != null) {
                        user.getCustomer().getId();
                    }
                    return new KeystoneUserDetails(user);
                })
                .orElseThrow(() -> new UsernameNotFoundException("No user with email " + email));
    }
}
