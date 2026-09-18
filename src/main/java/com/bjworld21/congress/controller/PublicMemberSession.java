package com.bjworld21.congress.controller;

import jakarta.servlet.http.HttpSession;

import java.util.function.LongSupplier;

record PublicMemberSession(long memberSeq, long conferenceSeq) {
    static PublicMemberSession resolve(HttpSession session, LongSupplier currentConferenceSeq) {
        if (session == null) {
            return null;
        }

        Object memberValue = session.getAttribute("memberSeq");
        Object conferenceValue = session.getAttribute("memberConferenceSeq");
        if (!(memberValue instanceof Number member) || !(conferenceValue instanceof Number conference)) {
            if (memberValue != null || conferenceValue != null) {
                session.invalidate();
            }
            return null;
        }

        long current = currentConferenceSeq.getAsLong();
        if (conference.longValue() != current) {
            session.invalidate();
            return null;
        }
        return new PublicMemberSession(member.longValue(), current);
    }
}
