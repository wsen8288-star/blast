package com.blastfurnace.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WarningStatsDTO {
    private Long tipCount;
    private Long warningCount;
    private Long severeCount;
}

