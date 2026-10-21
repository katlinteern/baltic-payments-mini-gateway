package com.baltic.gateway.platform.security;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            jwtService.parse(header.substring(7)).ifPresent(principal -> {
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
                MDC.put("username", principal.username());
            });
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("username");
        }
    }
}
