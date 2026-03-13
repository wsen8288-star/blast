package com.blastfurnace.backend.controller;

import com.blastfurnace.backend.dto.ComparisonHistoryDTO;
import com.blastfurnace.backend.dto.ComparisonHistoryDetailDTO;
import com.blastfurnace.backend.response.Result;
import com.blastfurnace.backend.service.ComparisonHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/comparison")
@RequiredArgsConstructor
public class ComparisonController {

    private final ComparisonHistoryService comparisonHistoryService;
    
    @GetMapping("/data")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasRole('USER') or hasAuthority('comparison:read')")
    public Result<Object> getComparisonData(@RequestParam(required = false) String params) {
        try {
            System.out.println("获取对比数据，参数: " + params);
            return Result.successMsg("获取对比数据成功");
        } catch (Exception e) {
            return Result.error("获取对比数据失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasRole('USER') or hasAuthority('comparison:read')")
    public Result<Object> exportComparisonData(@RequestParam(required = false) String params) {
        try {
            System.out.println("导出对比结果，参数: " + params);
            return Result.successMsg("导出对比结果成功");
        } catch (Exception e) {
            return Result.error("导出对比结果失败: " + e.getMessage());
        }
    }

    @GetMapping("/evolution/history")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasRole('USER') or hasAuthority('comparison:read')")
    public Result<List<ComparisonHistoryDTO>> getEvolutionHistory(@RequestParam(required = false) String mode,
                                                                  @RequestParam(required = false) String startDate,
                                                                  @RequestParam(required = false) String endDate) {
        try {
            Date start = parseDate(startDate, false);
            Date end = parseDate(endDate, true);
            return Result.success(comparisonHistoryService.getHistory(mode, start, end, "EVOLUTION"), "获取比较历史成功");
        } catch (Exception e) {
            return Result.error("获取比较历史失败: " + e.getMessage());
        }
    }

    @GetMapping("/evolution/history/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasRole('USER') or hasAuthority('comparison:read')")
    public Result<ComparisonHistoryDetailDTO> getEvolutionHistoryDetail(@PathVariable Long id) {
        try {
            return Result.success(comparisonHistoryService.getHistoryDetail(id), "获取比较详情成功");
        } catch (Exception e) {
            return Result.error("获取比较详情失败: " + e.getMessage());
        }
    }

    @GetMapping("/compare/history")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasRole('USER') or hasAuthority('comparison:read')")
    public Result<List<ComparisonHistoryDTO>> getCompareHistory(@RequestParam(required = false) String mode,
                                                                @RequestParam(required = false) String startDate,
                                                                @RequestParam(required = false) String endDate) {
        try {
            Date start = parseDate(startDate, false);
            Date end = parseDate(endDate, true);
            return Result.success(comparisonHistoryService.getHistory(mode, start, end, "COMPARISON"), "获取比较历史成功");
        } catch (Exception e) {
            return Result.error("获取比较历史失败: " + e.getMessage());
        }
    }

    @GetMapping("/compare/history/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasRole('USER') or hasAuthority('comparison:read')")
    public Result<ComparisonHistoryDetailDTO> getCompareHistoryDetail(@PathVariable Long id) {
        try {
            return Result.success(comparisonHistoryService.getHistoryDetail(id), "获取比较详情成功");
        } catch (Exception e) {
            return Result.error("获取比较详情失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/history/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasAuthority('comparison:write')")
    public Result<Void> deleteHistory(@PathVariable Long id) {
        try {
            comparisonHistoryService.deleteHistory(id);
            return Result.success(null, "删除成功");
        } catch (Exception e) {
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/history/batch")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasAuthority('comparison:write')")
    public Result<Void> batchDeleteHistory(@RequestBody List<Long> ids) {
        try {
            comparisonHistoryService.batchDeleteHistory(ids);
            return Result.success(null, "批量删除成功");
        } catch (Exception e) {
            return Result.error("批量删除失败: " + e.getMessage());
        }
    }

    @GetMapping("/evolution/compare")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasRole('USER') or hasAuthority('comparison:read')")
    public Result<Object> compareEvolution(@RequestParam Long historyIdA,
                                           @RequestParam Integer indexA,
                                           @RequestParam Long historyIdB,
                                           @RequestParam Integer indexB,
                                           @RequestParam(required = false) String furnaceId) {
        try {
            return Result.success(
                    comparisonHistoryService.compareSolutions(historyIdA, indexA, historyIdB, indexB, furnaceId),
                    "获取比较数据成功"
            );
        } catch (Exception e) {
            return Result.error("获取比较数据失败: " + e.getMessage());
        }
    }

    @GetMapping("/evolution/baseline/compare")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasRole('USER') or hasAuthority('comparison:read')")
    public Result<Object> compareEvolutionBaseline(@RequestParam Long historyId,
                                                   @RequestParam Integer index,
                                                   @RequestParam(required = false) String furnaceId,
                                                   @RequestParam(required = false) Long baselineId) {
        try {
            return Result.success(
                    comparisonHistoryService.compareEvolutionWithBaseline(historyId, index, furnaceId, baselineId),
                    "获取比较数据成功"
            );
        } catch (Exception e) {
            return Result.error("获取比较数据失败: " + e.getMessage());
        }
    }

    @GetMapping("/production/compare")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasRole('USER') or hasAuthority('comparison:read')")
    public Result<Object> compareProduction(@RequestParam Long dataIdA,
                                            @RequestParam Long dataIdB) {
        try {
            return Result.success(
                    comparisonHistoryService.compareProductionData(dataIdA, dataIdB),
                    "获取比较数据成功"
            );
        } catch (Exception e) {
            return Result.error("获取比较数据失败: " + e.getMessage());
        }
    }

    @PostMapping("/adopt/{historyId}/{schemeIndex}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasAuthority('comparison:write')")
    public Result<String> adoptScheme(@PathVariable Long historyId, @PathVariable Integer schemeIndex) {
        try {
            String instruction = comparisonHistoryService.adoptScheme(historyId, schemeIndex);
            return Result.success(instruction, "采纳方案成功");
        } catch (Exception e) {
            return Result.error("采纳方案失败: " + e.getMessage());
        }
    }

    private Date parseDate(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        try {
            return Date.from(Instant.parse(text));
        } catch (Exception ignored) {
        }
        try {
            LocalDateTime dateTime = LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
        } catch (Exception ignored) {
        }
        try {
            LocalDateTime dateTime = LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"));
            return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
        } catch (Exception ignored) {
        }
        try {
            LocalDate date = LocalDate.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            LocalDateTime dateTime = endOfDay ? date.atTime(LocalTime.MAX) : date.atStartOfDay();
            return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
        } catch (Exception ignored) {
        }
        try {
            LocalDate date = LocalDate.parse(text, DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            LocalDateTime dateTime = endOfDay ? date.atTime(LocalTime.MAX) : date.atStartOfDay();
            return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
        } catch (Exception ignored) {
        }
        return null;
    }
}
