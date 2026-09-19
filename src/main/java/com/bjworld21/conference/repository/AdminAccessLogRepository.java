package com.bjworld21.conference.repository;

import com.bjworld21.conference.entity.AdminAccessLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminAccessLogRepository {

    @Insert("""
            INSERT INTO admin_access_logs (
                adminSeq,
                adminEmail,
                adminName,
                adminRole,
                ipAddress,
                userAgent,
                loginAt
            ) VALUES (
                #{log.adminSeq},
                HEX(AES_ENCRYPT(#{log.adminEmail}, SHA2(#{dbEncString}, 512))),
                HEX(AES_ENCRYPT(#{log.adminName}, SHA2(#{dbEncString}, 512))),
                #{log.adminRole},
                #{log.ipAddress},
                #{log.userAgent},
                NOW(6)
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "log.seq")
    void insert(@Param("log") AdminAccessLog accessLog, @Param("dbEncString") String dbEncString);
}
