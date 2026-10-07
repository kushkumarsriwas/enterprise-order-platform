package com.enterprise.auth.service;

import com.enterprise.auth.dto.RegisterRequest;
import com.enterprise.auth.dto.UserResponse;
import com.enterprise.auth.entity.User;
import com.enterprise.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerUserShouldCreateUserResponse() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setEmail("test@example.com");
        request.setPassword("plainPassword");

        when(userRepository.findByUsername("testuser")).thenReturn(java.util.Optional.empty());
        when(userRepository.findByEmail("test@example.com")).thenReturn(java.util.Optional.empty());
        when(passwordEncoder.encode("plainPassword")).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        UserResponse result = authService.register(request);

        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
        assertEquals("test@example.com", result.getEmail());
        verify(passwordEncoder).encode("plainPassword");
        verify(userRepository).save(any(User.class));
    }
}
