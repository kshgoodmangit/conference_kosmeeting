package com.bjworld21.conference.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.abstract-similarity")
public class AbstractSimilarityProperties {
    private int dimension = 384;
    private int topK = 10;
    private Weights weights = new Weights();
    private ReviewTarget reviewTarget = new ReviewTarget();
    private Server server = new Server();

    @Data
    public static class Weights {
        private int title = 10;
        private int objective = 20;
        private int methods = 20;
        private int results = 30;
        private int conclusions = 20;
    }

    @Data
    public static class ReviewTarget {
        private double overallThreshold = 80.0;
        private double sectionThreshold = 90.0;
        private double criticalOverallThreshold = 90.0;
        private double criticalSectionThreshold = 95.0;
    }

    @Data
    public static class Server {
        private String baseUrl = "http://127.0.0.1:8001";
        private String healthPath = "/health";
        private String embeddingsPath = "/embeddings";
        private int connectTimeoutSeconds = 3;
        private int readTimeoutSeconds = 30;
    }
}
