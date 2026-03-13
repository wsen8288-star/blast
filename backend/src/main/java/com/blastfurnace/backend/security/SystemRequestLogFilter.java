package com.blastfurnace.backend.security;

import com.blastfurnace.backend.model.SystemRequestLog;
import com.blastfurnace.backend.service.SystemRequestLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class SystemRequestLogFilter extends OncePerRequestFilter {
    private final SystemRequestLogService systemRequestLogService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && (uri.startsWith("/api/ws/") || uri.startsWith("/api/system/logs"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        Throwable throwable = null;
        try {
            filterChain.doFilter(request, response);
        } catch (Throwable ex) {
            throwable = ex;
            throw ex;
        } finally {
            try {
                saveLog(request, response, start, throwable);
            } catch (Exception ignored) {
            }
        }
    }

    private void saveLog(HttpServletRequest request, HttpServletResponse response, long start, Throwable throwable) {
        int statusCode = response.getStatus();
        if (throwable != null && statusCode < 400) {
            statusCode = 500;
        }
        String level = statusCode >= 500 ? "ERROR" : statusCode >= 400 ? "WARN" : "INFO";
        if ("INFO".equals(level)) {
            return;
        }
        SystemRequestLog log = new SystemRequestLog();
        log.setRequestMethod(request.getMethod());
        log.setRequestUri(trim(request.getRequestURI(), 512));
        log.setQueryString(trim(request.getQueryString(), 1024));
        log.setStatusCode(statusCode);
        log.setDurationMs(Math.max(System.currentTimeMillis() - start, 0));
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String name = authentication.getName();
            if (name != null && !"anonymousUser".equalsIgnoreCase(name)) {
                log.setUsername(trim(name, 128));
            }
        }
        log.setClientIp(trim(resolveClientIp(request), 64));
        log.setUserAgent(trim(request.getHeader("User-Agent"), 512));
        if (throwable != null) {
            String message = throwable.getClass().getSimpleName() + ": " + (throwable.getMessage() == null ? "" : throwable.getMessage());
            log.setErrorMessage(trim(message, 1024));
        }
        log.setLevel(level);
        systemRequestLogService.save(log);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            if (parts.length > 0) {
                return parts[0].trim();
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (v.length() <= maxLength) {
            return v;
        }
        return v.substring(0, maxLength);
    }
}
