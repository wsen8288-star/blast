package com.blastfurnace.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WarningHandleRequestDTO {
    private Long id;
    private Long handlerUser;
    private String handlerContent;
    private Integer status;
}
