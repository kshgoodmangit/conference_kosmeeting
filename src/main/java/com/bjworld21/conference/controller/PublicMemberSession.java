package com.bjworld21.conference.controller;

import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.util.Collections;
import java.util.function.LongSupplier;

/** Separate authentication per conference in the same browser HTTP session. */
public record PublicMemberSession(long memberSeq, long conferenceSeq) implements Serializable {
    private static final String PREFIX = "publicMember.";

    public static PublicMemberSession resolve(HttpSession session, LongSupplier currentConferenceSeq) {
        return resolve(session, currentConferenceSeq.getAsLong());
    }

    public static PublicMemberSession resolve(HttpSession session, long conferenceSeq) {
        if (session == null) return null;
        Object value = session.getAttribute(PREFIX + conferenceSeq);
        return value instanceof PublicMemberSession member && member.conferenceSeq == conferenceSeq ? member : null;
    }

    public static void signIn(HttpSession session, long conferenceSeq, long memberSeq, String fingerprint) {
        session.setAttribute(PREFIX + conferenceSeq, new PublicMemberSession(memberSeq, conferenceSeq));
        session.setAttribute(PREFIX + conferenceSeq + ".credential", fingerprint);
    }

    public static String fingerprint(HttpSession session, long conferenceSeq) {
        if (session == null) return null;
        Object value = session.getAttribute(PREFIX + conferenceSeq + ".credential");
        return value instanceof String fingerprint ? fingerprint : null;
    }

    public static void signOut(HttpSession session, long conferenceSeq) {
        if (session == null) return;
        session.removeAttribute(PREFIX + conferenceSeq);
        session.removeAttribute(PREFIX + conferenceSeq + ".credential");
    }

    public static java.util.List<PublicMemberSession> all(HttpSession session) {
        if (session == null) return java.util.List.of();
        return Collections.list(session.getAttributeNames()).stream()
                .filter(name -> name.startsWith(PREFIX))
                .map(session::getAttribute).filter(PublicMemberSession.class::isInstance)
                .map(PublicMemberSession.class::cast).toList();
    }
}
