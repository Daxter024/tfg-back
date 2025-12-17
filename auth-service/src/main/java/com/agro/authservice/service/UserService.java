package com.agro.authservice.service;

import com.agro.authservice.event.UserDeletedEvent;
import com.agro.authservice.kafka.EventPublisher;
import com.agro.authservice.model.User;
import com.agro.authservice.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final EventPublisher eventPublisher;

    public UserService(UserRepository userRepository, EventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }


    @Transactional
    public void deleteUser(java.util.UUID userId) {
        if (userRepository.existsById(userId)) {
            userRepository.deleteById(userId);
            eventPublisher.publishUserDeleted(new UserDeletedEvent(userId));
        }
    }
}
