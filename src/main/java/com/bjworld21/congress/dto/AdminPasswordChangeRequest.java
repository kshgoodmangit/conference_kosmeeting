package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminPasswordChangeRequest {
    private String currentPassword;
    private String newPassword;
    private String newPasswordConfirm;
}
