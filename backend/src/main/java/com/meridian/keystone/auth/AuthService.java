package com.meridian.keystone.auth;

import com.meridian.keystone.dto.LoginRequest;
import com.meridian.keystone.dto.LoginResponse;
import com.meridian.keystone.dto.UserView;
import com.meridian.keystone.security.JwtService;
import com.meridian.keystone.security.KeystoneUserDetails;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Authenticates credentials and issues a JWT. Delegates the actual credential
 * check to Spring Security's {@link AuthenticationManager} (which uses the
 * BCrypt encoder + user-details service), then mints a token for the principal.
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (BadCredentialsException ex) {
            // Normalise to a single message so we never reveal which part was wrong.
            throw new BadCredentialsException("Invalid email or password");
        }

        KeystoneUserDetails principal = (KeystoneUserDetails) authentication.getPrincipal();
        String token = jwtService.generateToken(principal);
        return LoginResponse.of(token, jwtService.getExpirationMinutes(),
                UserView.from(principal.getUser()));
    }
}
