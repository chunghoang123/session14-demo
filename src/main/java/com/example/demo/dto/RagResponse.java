package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RagResponse {
    private String query;
    private String answer;
    private List<Citation> citations;
    private boolean foundInDocuments;
    private double similarityScore;
    private long executionTimeMs;
    private ChunkConfig chunkConfig;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Citation {
        private String documentName;
        private String section;
        private String pageNumber;
        private String snippet;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChunkConfig {
        private int chunkSize;
        private int overlapPercent;
        private double similarityThreshold;
    }
}
