package com.bjworld21.conference.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class OperationalDashboardResponse {
    private OperationalDashboardResponse() {}

    public record Deadline(String label, LocalDate date) {}
    public record Registration(LocalDate asOf, LocalDateTime generatedAt, String eventName,
            List<Deadline> deadlines, List<RegistrationCategory> categories,
            List<Amount> amounts, List<Daily> daily, List<Period> periods) {}
    public record Abstracts(LocalDate asOf, LocalDateTime generatedAt, String eventName,
            List<Deadline> deadlines, List<Field> fields, List<Decision> decisions,
            long pendingDecisionCount, List<Presenter> presenters, List<Daily> daily) {}

    @Data public static class RegistrationCategory {
        private long categorySeq;
        private String categoryName;
        private long paid;
        private long unpaid;
        private long longUnpaid;
        private long cancelled;
        private long refunded;
    }
    @Data public static class Amount {
        private String currency;
        private BigDecimal paid;
        private BigDecimal todayPaid;
        private BigDecimal unpaid;
    }
    @Data public static class Period {
        private String label;
        private long count;
    }
    @Data public static class Daily {
        private LocalDate date;
        private long registered;
        private long paid;
        private long submitted;
    }
    @Data public static class Field {
        private long categorySeq;
        private String name;
        private long unassigned;
        private long reviewing;
        private long completed;
        private long overdue;
    }
    @Data public static class Decision {
        private String key;
        private String label;
        private long value;
    }
    @Data public static class Presenter {
        private long presentationTypeSeq;
        private String label;
        private long registered;
        private long unregistered;
    }
}
