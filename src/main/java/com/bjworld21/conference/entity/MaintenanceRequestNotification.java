package com.bjworld21.conference.entity;

import lombok.Data;

@Data
public class MaintenanceRequestNotification {
    private Long seq;
    private Long maintenanceRequestSeq;
    private Long recipientAdminSeq;
}
