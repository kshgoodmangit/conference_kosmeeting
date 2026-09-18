package com.bjworld21.congress.service;

import java.util.LinkedHashSet;
import java.util.List;

public final class RecipientGroups {
    private RecipientGroups() {}
    public static final List<String> CODES = List.of("ALL_MEMBERS", "ALL_REGISTRANTS", "ALL_SUBMITTERS", "ALL_ACCEPTED");
    public static final String MEMBER_PREDICATE = """
            (#{group} = 'ALL_MEMBERS'
             OR (#{group} = 'ALL_REGISTRANTS' AND EXISTS (
                 SELECT 1 FROM pre_registrations p WHERE p.memberSeq = m.seq
                 AND p.conferenceSeq = m.conferenceSeq AND p.applicationStatus = 'SUBMITTED'))
             OR (#{group} = 'ALL_SUBMITTERS' AND EXISTS (
                 SELECT 1 FROM abstract_submissions s WHERE s.memberSeq = m.seq
                 AND s.conferenceSeq = m.conferenceSeq
                 AND s.status IN ('submitted', 'under_review', 'approved', 'rejected')))
             OR (#{group} = 'ALL_ACCEPTED' AND EXISTS (
                 SELECT 1 FROM abstract_submissions s WHERE s.memberSeq = m.seq
                 AND s.conferenceSeq = m.conferenceSeq AND s.status = 'approved')))
            """;
    public static List<String> validate(List<String> groups) {
        if (groups == null) return List.of();
        if (groups.stream().anyMatch(group -> group == null || !CODES.contains(group))) {
            throw new IllegalArgumentException("전체 수신 대상 구분이 올바르지 않습니다.");
        }
        return List.copyOf(new LinkedHashSet<>(groups));
    }
}
