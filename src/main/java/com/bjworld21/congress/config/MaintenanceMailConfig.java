package com.bjworld21.congress.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class MaintenanceMailConfig {
    @Bean("maintenanceMailSender")
    public JavaMailSender maintenanceMailSender(MaintenanceMailProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.getHost());
        sender.setPort(properties.getPort());
        sender.setUsername(properties.getUsername());
        sender.setPassword(properties.getPassword());
        sender.setDefaultEncoding("UTF-8");

        Properties mailProperties = sender.getJavaMailProperties();
        mailProperties.put("mail.smtp.auth", Boolean.toString(properties.isAuthEnabled()));
        mailProperties.put("mail.smtp.starttls.enable", Boolean.toString(properties.isStarttlsEnabled()));
        mailProperties.put("mail.smtp.connectiontimeout", Integer.toString(properties.getConnectionTimeoutMillis()));
        mailProperties.put("mail.smtp.timeout", Integer.toString(properties.getReadTimeoutMillis()));
        mailProperties.put("mail.smtp.writetimeout", Integer.toString(properties.getWriteTimeoutMillis()));
        return sender;
    }
}
