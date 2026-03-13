package com.blastfurnace.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComparisonHistoryDTO {
    private Long id;
    private Date createdAt;
    private String mode;
    private String schemeA;
    private String schemeB;
    private String result;
    private Double scoreA;
    private Double scoreB;
    private Date baselineTime;
    private String historyType;
}
