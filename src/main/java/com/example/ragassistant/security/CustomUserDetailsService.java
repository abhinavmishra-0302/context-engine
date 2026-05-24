package com.example.ragassistant.security;

import com.example.ragassistant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserPrincipal loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByEmail(username)
                .map(user -> UserPrincipal.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .passwordHash(user.getPasswordHash())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
