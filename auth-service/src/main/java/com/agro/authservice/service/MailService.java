package com.agro.authservice.service;

public interface MailService {
    void send(String to, String subject, String body);
}
