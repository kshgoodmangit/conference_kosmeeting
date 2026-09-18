package com.bjworld21.congress.service;

import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.dto.AdminLoginResponse;
import com.bjworld21.congress.entity.AdminAccessLog;
import com.bjworld21.congress.repository.AdminAccessLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class AdminAccessLogService {
    private static final int MAX_IP_ADDRESS_LENGTH = 45;
    private static final int MAX_USER_AGENT_LENGTH = 500;

    private final AdminAccessLogRepository adminAccessLogRepository;
    private final PersonalDataProperties personalDataProperties;

    public AdminAccessLogService(
            AdminAccessLogRepository adminAccessLogRepository,
            PersonalDataProperties personalDataProperties
    ) {
        this.adminAccessLogRepository = adminAccessLogRepository;
        this.personalDataProperties = personalDataProperties;
    }

    public void recordSuccessfulLogin(AdminLoginResponse loginResponse, HttpServletRequest request) {
        AdminAccessLog accessLog = AdminAccessLog.builder()
                .adminSeq(loginResponse.getSeq())
                .adminEmail(loginResponse.getEmail())
                .adminName(loginResponse.getAdminName())
                .adminRole(loginResponse.getRole())
                .ipAddress(limitLength(trimToNull(request.getRemoteAddr()), MAX_IP_ADDRESS_LENGTH))
                .userAgent(limitLength(trimToNull(request.getHeader("User-Agent")), MAX_USER_AGENT_LENGTH))
                .build();

        adminAccessLogRepository.insert(accessLog, personalDataProperties.requireDbEncString());
    }

    private String limitLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
