package com.agro.taskservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Wrapper de {@link JavaMailSender}. Si {@code spring.mail.host} esta vacio,
 * loguea en lugar de enviar (modo degradado documentado en §9.5 del plan).
 *
 * <p>{@link JavaMailSender} se inyecta como {@code @Autowired(required=false)}
 * para que la falta de configuracion SMTP no rompa el ApplicationContext.
 * Spring crea el bean automaticamente cuando hay {@code spring.mail.*} pero
 * no necesita estar presente para que arranque el resto del servicio.</p>
 */
@Service
@Slf4j
public class MailService {

    private final JavaMailSender mailSender;
    private final String host;
    private final String from;

    public MailService(@Autowired(required = false) JavaMailSender mailSender,
                       @Value("${spring.mail.host:}") String host,
                       @Value("${spring.mail.from:noreply@agro.local}") String from) {
        this.mailSender = mailSender;
        this.host = host;
        this.from = from;
    }

    public boolean isEnabled() {
        return mailSender != null && host != null && !host.isBlank();
    }

    public void send(String to, String subject, String body) {
        if (!isEnabled()) {
            log.info("[mail disabled] to={} subject={}", to, subject);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
        } catch (Exception e) {
            log.warn("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
