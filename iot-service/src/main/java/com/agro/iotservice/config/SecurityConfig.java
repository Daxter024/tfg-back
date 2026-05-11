package com.agro.iotservice.config;

import com.agro.iotservice.security.DeviceKeyAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security config — iot-service relies on the gateway for JWT validation on
 * admin routes (/sensor, /threshold, /alert). The dedicated ingest path
 * /ingest/** is authenticated by {@link DeviceKeyAuthFilter} against
 * BCrypt-hashed API keys; everything else stays open at the service level
 * (the gateway already gates it).
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final DeviceKeyAuthFilter deviceKeyAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(deviceKeyAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
