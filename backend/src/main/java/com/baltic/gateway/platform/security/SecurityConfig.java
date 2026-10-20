package com.baltic.gateway.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import com.baltic.gateway.platform.web.ErrorCode;
import com.baltic.gateway.platform.web.GlobalExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService, ObjectMapper mapper) throws Exception {
        // CSRF protection is unnecessary: authentication uses a bearer header, never ambient cookies.
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
            .authorizeHttpRequests(auth -> auth
                // Served only on the separate management port (8081), which is never published: Prometheus scrapes
                // the metrics and operations staff run the ledger reconciliation.
                // On the public port these paths do not exist (ObservabilityIntegrationTest asserts that).
                .requestMatchers("/actuator/prometheus", "/actuator/reconciliation").permitAll()
                .requestMatchers("/api/auth/login", "/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) ->
                    writeProblem(mapper, response, ErrorCode.UNAUTHORIZED, "Authentication required"))
                .accessDeniedHandler((request, response, exception) ->
                    writeProblem(mapper, response, ErrorCode.FORBIDDEN, "Access denied")))
            .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void writeProblem(ObjectMapper mapper, HttpServletResponse response, ErrorCode code,
                                     String detail) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType("application/problem+json");
        mapper.writeValue(response.getOutputStream(), GlobalExceptionHandler.problemDetail(code, detail));
    }
}
