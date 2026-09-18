package com.bjworld21.congress.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SponsorshipApplication {
    private Long seq;
    private Long conferenceSeq;
    private String companyKrName;
    private String companyEnName;
    private String ceoName;
    private String businessNumber;
    private String zonecode;
    private String address;
    private String addressDetail;
    private String sponsorshipType;
    private Long sponsorshipAmount;
    private String businessLicenseOriFilename;
    private String businessLicenseSaveFilename;
    private String contactPersonName;
    private String contactPersonPosition;
    private String contactPersonDepartment;
    private String contactPersonPhone;
    private String contactPersonMobile;
    private String contactPersonEmail;
    private String faxNumber;
    private Boolean isDeposited;
    private LocalDate depositDate;
    private LocalDate expectedDepositDate;
    private String taxInvoiceRecipient;
    private String taxInvoiceEmail;
    private LocalDate taxInvoiceIssueDate;
    private String taxInvoiceType;
    private String remarks;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
