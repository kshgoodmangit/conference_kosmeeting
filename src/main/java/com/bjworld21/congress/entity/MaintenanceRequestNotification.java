package com.bjworld21.congress.entity;

import lombok.Data;

@Data
public class MaintenanceRequestNotification {
    private Long seq;
    private Long maintenanceRequestSeq;
    private Long recipientAdminSeq;
}
