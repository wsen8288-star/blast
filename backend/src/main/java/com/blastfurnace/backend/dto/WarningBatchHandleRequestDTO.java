package com.blastfurnace.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WarningBatchHandleRequestDTO {
    private List<Long> ids;
    private Long handlerUser;
    private String handlerContent;
    private Integer status;
    private Boolean allowSevere;
}
