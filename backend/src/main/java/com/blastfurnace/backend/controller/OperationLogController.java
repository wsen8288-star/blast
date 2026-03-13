package com.blastfurnace.backend.controller;

import com.blastfurnace.backend.response.Result;
import com.blastfurnace.backend.model.OperationLog;
import com.blastfurnace.backend.repository.OperationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operation")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogRepository operationLogRepository;

    @GetMapping("/logs")
    public Result<Page<OperationLog>> getLogs(
            @RequestParam(required = false) Long schemeId,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String keyword,
            @PageableDefault(sort = "executionTime", direction = Sort.Direction.DESC, size = 50) Pageable pageable) {
        int pageNumber = Math.max(pageable.getPageNumber(), 0);
        int pageSize = Math.min(Math.max(pageable.getPageSize(), 1), 200);
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "executionTime");
        Pageable safePageable = PageRequest.of(pageNumber, pageSize, sort);
        Specification<OperationLog> specification = (root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (schemeId != null) {
                predicates.add(cb.equal(root.get("schemeId"), schemeId));
            }
            if (operator != null && !operator.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("operator")), "%" + operator.trim().toLowerCase() + "%"));
            }
            if (keyword != null && !keyword.isBlank()) {
                String k = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(
                        cb.or(
                                cb.like(cb.lower(root.get("adjustments")), k),
                                cb.like(cb.lower(root.get("operator")), k)
                        )
                );
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Page<OperationLog> logs = operationLogRepository.findAll(specification, safePageable);
        return Result.success(logs, "获取操作日志成功");
    }
}
