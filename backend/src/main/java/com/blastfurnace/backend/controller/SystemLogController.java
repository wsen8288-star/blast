package com.blastfurnace.backend.controller;

import com.blastfurnace.backend.model.SystemRequestLog;
import com.blastfurnace.backend.response.Result;
import com.blastfurnace.backend.service.SystemRequestLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

@RestController
@RequestMapping("/api/system/logs")
@RequiredArgsConstructor
public class SystemLogController {
    private final SystemRequestLogService systemRequestLogService;

    @GetMapping
    public Result<Page<SystemRequestLog>> queryLogs(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date endTime,
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<SystemRequestLog> page = systemRequestLogService.query(level, method, keyword, username, startTime, endTime, pageable);
        return Result.success(page);
    }
}

