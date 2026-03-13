package com.blastfurnace.backend.aspect;

import com.blastfurnace.backend.annotation.LogAction;
import com.blastfurnace.backend.model.OperationLog;
import com.blastfurnace.backend.repository.OperationLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Date;

@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogRepository operationLogRepository;

    @Around("@annotation(logAction)")
    public Object around(ProceedingJoinPoint joinPoint, LogAction logAction) throws Throwable {
        Object result = joinPoint.proceed();
        try {
            OperationLog log = new OperationLog();
            log.setSchemeId(0L);
            log.setExecutionTime(new Date());
            log.setOperator(resolveUsername());
            log.setAdjustments(buildAdjustments(logAction));
            operationLogRepository.save(log);
        } catch (Exception ignored) {
        }
        return result;
    }

    private String resolveUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
                return "anonymous";
            }
            return auth.getName();
        } catch (Exception e) {
            return "anonymous";
        }
    }

    private String buildAdjustments(LogAction logAction) {
        String module = logAction == null ? "" : safe(logAction.module());
        String action = logAction == null ? "" : safe(logAction.value());
        String uri = "";
        String ip = "";
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            HttpServletRequest request = attrs == null ? null : attrs.getRequest();
            if (request != null) {
                uri = safe(request.getRequestURI());
                ip = safe(request.getRemoteAddr());
            }
        } catch (Exception ignored) {
        }
        return "{\"module\":\"" + module + "\",\"action\":\"" + action + "\",\"uri\":\"" + uri + "\",\"ip\":\"" + ip + "\",\"status\":\"SUCCESS\"}";
    }

    private String safe(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

