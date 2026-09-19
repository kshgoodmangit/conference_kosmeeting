package com.bjworld21.conference.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MemberPasswordResetToken {
    private String tokenHash;
    private Long conferenceSeq;
    private Long memberSeq;
    private String credentialFingerprint;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
}
