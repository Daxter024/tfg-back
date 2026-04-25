package com.agro.authservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LogMailService implements MailService {

    @Override
    public void send(String to, String subject, String body) {
        log.info("[MailService][log-only] to={} subject=\"{}\" body=\"{}\"", to, subject, body);
    }
}
