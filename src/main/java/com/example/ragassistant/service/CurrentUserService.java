package com.example.ragassistant.service;

import com.example.ragassistant.exception.AppException;
import com.example.ragassistant.model.User;
import com.example.ragassistant.repository.UserRepository;
import com.example.ragassistant.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return userRepository.findById(principal.id())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}
