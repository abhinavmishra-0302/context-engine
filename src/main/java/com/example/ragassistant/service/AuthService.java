package com.example.ragassistant.service;

import com.example.ragassistant.dto.LoginRequest;
import com.example.ragassistant.dto.LoginResponse;
import com.example.ragassistant.dto.SignupRequest;
import com.example.ragassistant.dto.SignupResponse;
import com.example.ragassistant.exception.AppException;
import com.example.ragassistant.model.User;
import com.example.ragassistant.repository.UserRepository;
import com.example.ragassistant.security.JwtService;
import com.example.ragassistant.security.UserPrincipal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public SignupResponse signup(SignupRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        userRepository.findByEmail(normalizedEmail).ifPresent(user -> {
            throw new AppException(HttpStatus.CONFLICT, "Email already registered");
        });

        User user = User.builder()
                .id(UUID.randomUUID())
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .createdAt(Instant.now())
                .build();
        userRepository.save(user);

        String token = jwtService.generateToken(toPrincipal(user));
        return new SignupResponse(user.getId(), token);
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return new LoginResponse(jwtService.generateToken(toPrincipal(user)));
    }

    private UserPrincipal toPrincipal(User user) {
        return UserPrincipal.builder()
                .id(user.getId())
                .email(user.getEmail())
                .passwordHash(user.getPasswordHash())
                .build();
    }
}
