package com.blastfurnace.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class Utf8JsonResponseFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        response.setCharacterEncoding("UTF-8");
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (response.isCommitted()) {
                return;
            }
            String contentType = response.getContentType();
            if (contentType == null) {
                return;
            }
            String lower = contentType.toLowerCase(Locale.ROOT);
            if (lower.startsWith("application/json") && !lower.contains("charset=")) {
                response.setContentType("application/json;charset=UTF-8");
            }
        }
    }
}
