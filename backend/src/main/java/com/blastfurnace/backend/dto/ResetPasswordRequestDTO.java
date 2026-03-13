package com.blastfurnace.backend.dto;

import lombok.Data;

@Data
public class ResetPasswordRequestDTO {
    private String email;
    private String newPassword;
    private String confirmPassword;
}