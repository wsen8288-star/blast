package com.blastfurnace.backend.service;

import com.blastfurnace.backend.model.SystemRequestLog;
import com.blastfurnace.backend.repository.SystemRequestLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemRequestLogService {
    private final SystemRequestLogRepository systemRequestLogRepository;

    public void save(SystemRequestLog log) {
        if (log == null) {
            return;
        }
        systemRequestLogRepository.save(log);
    }

    public Page<SystemRequestLog> query(String level, String method, String keyword, String username, Date startTime, Date endTime, Pageable pageable) {
        return systemRequestLogRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (level != null && !level.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("level")), level.trim().toUpperCase()));
            }
            if (method != null && !method.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("requestMethod")), method.trim().toUpperCase()));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("requestUri")), like));
            }
            if (username != null && !username.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("username")), username.trim().toLowerCase()));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }
}

