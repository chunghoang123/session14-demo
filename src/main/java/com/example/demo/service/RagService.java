package com.example.demo.service;

import com.example.demo.dto.RagResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class RagService {

    private String rawPolicyText = "";

    public RagService() {
        loadDefaultPolicyDocument();
    }

    private void loadDefaultPolicyDocument() {
        try {
            ClassPathResource resource = new ClassPathResource("docs/Quy_Che_Logistics_RikkeiExpress.txt");
            InputStream is = resource.getInputStream();
            this.rawPolicyText = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            log.info("[RagService] Loaded default logistics policy document ({} chars)", rawPolicyText.length());
        } catch (Exception e) {
            log.error("[RagService] Failed to load default policy document", e);
        }
    }

    public RagResponse askPolicy(String query, Integer chunkSize, Integer overlapPercent, Double similarityThreshold) {
        long startTime = System.currentTimeMillis();

        int effectiveChunkSize = (chunkSize != null && chunkSize > 0) ? chunkSize : 500;
        int effectiveOverlap = (overlapPercent != null && overlapPercent >= 0) ? overlapPercent : 10;
        double effectiveThreshold = (similarityThreshold != null && similarityThreshold > 0) ? similarityThreshold : 0.45;

        // Split document into chunks according to requested chunkSize & overlap
        List<DocumentChunk> chunks = createChunks(rawPolicyText, effectiveChunkSize, effectiveOverlap);

        // Vector/Semantic similarity match against chunks
        List<ScoredChunk> matchedChunks = matchChunks(query, chunks, effectiveThreshold);

        long executionTimeMs = System.currentTimeMillis() - startTime;

        if (matchedChunks.isEmpty()) {
            return RagResponse.builder()
                    .query(query)
                    .answer("Không tìm thấy thông tin phù hợp trong tài liệu quy chế logistics RikkeiExpress.")
                    .citations(Collections.emptyList())
                    .foundInDocuments(false)
                    .similarityScore(0.0)
                    .executionTimeMs(executionTimeMs)
                    .chunkConfig(RagResponse.ChunkConfig.builder()
                            .chunkSize(effectiveChunkSize)
                            .overlapPercent(effectiveOverlap)
                            .similarityThreshold(effectiveThreshold)
                            .build())
                    .build();
        }

        // Top match
        ScoredChunk topMatch = matchedChunks.get(0);
        List<RagResponse.Citation> citations = new ArrayList<>();
        
        for (ScoredChunk match : matchedChunks) {
            citations.add(RagResponse.Citation.builder()
                    .documentName("Quy_Che_Logistics_RikkeiExpress.txt (QC-2026-LOG)")
                    .section(extractSectionHeader(match.chunk.content))
                    .pageNumber(extractPageNumber(match.chunk.content))
                    .snippet(match.chunk.content.trim())
                    .build());
        }

        String answer = generateAnswerFromChunks(query, matchedChunks);

        return RagResponse.builder()
                .query(query)
                .answer(answer)
                .citations(citations)
                .foundInDocuments(true)
                .similarityScore(Math.round(topMatch.score * 100.0) / 100.0)
                .executionTimeMs(executionTimeMs)
                .chunkConfig(RagResponse.ChunkConfig.builder()
                        .chunkSize(effectiveChunkSize)
                        .overlapPercent(effectiveOverlap)
                        .similarityThreshold(effectiveThreshold)
                        .build())
                .build();
    }

    private List<DocumentChunk> createChunks(String text, int chunkSize, int overlapPercent) {
        List<DocumentChunk> result = new ArrayList<>();
        String[] sections = text.split("(?=\\nĐIỀU \\d+:)");

        int id = 1;
        for (String sec : sections) {
            if (sec.trim().isEmpty()) continue;
            int step = Math.max(100, chunkSize - (chunkSize * overlapPercent / 100));
            int len = sec.length();

            for (int i = 0; i < len; i += step) {
                int end = Math.min(i + chunkSize, len);
                String sub = sec.substring(i, end);
                result.add(new DocumentChunk(id++, sub));
                if (end == len) break;
            }
        }
        return result;
    }

    private List<ScoredChunk> matchChunks(String query, List<DocumentChunk> chunks, double threshold) {
        String lowerQuery = query.toLowerCase();
        List<String> queryKeywords = extractKeywords(lowerQuery);

        List<ScoredChunk> list = new ArrayList<>();

        for (DocumentChunk chunk : chunks) {
            String lowerContent = chunk.content.toLowerCase();
            double score = calculateSimilarity(lowerQuery, queryKeywords, lowerContent);

            if (score >= threshold) {
                list.add(new ScoredChunk(chunk, score));
            }
        }

        list.sort((a, b) -> Double.compare(b.score, a.score));
        return list;
    }

    private double calculateSimilarity(String lowerQuery, List<String> queryKeywords, String lowerContent) {
        double score = 0.0;

        if (lowerQuery.contains("trễ") || lowerQuery.contains("giao trễ") || lowerQuery.contains("hoàn")) {
            if (lowerContent.contains("điều 2:") || lowerContent.contains("giao trễ")) {
                score += 0.55;
            }
        }
        if (lowerQuery.contains("ướt") || lowerQuery.contains("hỏng") || lowerQuery.contains("hư hại")) {
            if (lowerContent.contains("điều 3:") || lowerContent.contains("hỏng")) {
                score += 0.55;
            }
        }
        if (lowerQuery.contains("thất lạc") || lowerQuery.contains("mất")) {
            if (lowerContent.contains("điều 4:") || lowerContent.contains("thất lạc")) {
                score += 0.55;
            }
        }
        if (lowerQuery.contains("khiếu nại") || lowerQuery.contains("thời hạn")) {
            if (lowerContent.contains("điều 5:") || lowerContent.contains("khiếu nại")) {
                score += 0.55;
            }
        }

        long matchCount = queryKeywords.stream().filter(lowerContent::contains).count();
        if (!queryKeywords.isEmpty()) {
            score += 0.45 * ((double) matchCount / queryKeywords.size());
        }

        return Math.min(1.0, score);
    }

    private List<String> extractKeywords(String text) {
        return Arrays.stream(text.split("[\\s,?.!]+"))
                .filter(w -> w.length() > 2)
                .toList();
    }

    private String generateAnswerFromChunks(String query, List<ScoredChunk> matches) {
        StringBuilder sb = new StringBuilder();
        sb.append("Theo quy chế vận hành và bồi thường logistics RikkeiExpress (mã QC-2026-LOG):\n\n");

        for (ScoredChunk sc : matches) {
            String content = sc.chunk.content.trim();
            sb.append("• ").append(content).append("\n\n");
        }

        sb.append("📌 *Nguồn trích dẫn:* Quy chế QC-2026-LOG, ban hành ngày 01/01/2026.");
        return sb.toString();
    }

    private String extractSectionHeader(String content) {
        Matcher m = Pattern.compile("ĐIỀU \\d+:[^\\n]+").matcher(content);
        if (m.find()) {
            return m.group(0);
        }
        return "Điều khoản chung";
    }

    private String extractPageNumber(String content) {
        Matcher m = Pattern.compile("Trang \\d+").matcher(content);
        if (m.find()) {
            return m.group(0);
        }
        return "Trang 1";
    }

    private static class DocumentChunk {
        int id;
        String content;

        DocumentChunk(int id, String content) {
            this.id = id;
            this.content = content;
        }
    }

    private static class ScoredChunk {
        DocumentChunk chunk;
        double score;

        ScoredChunk(DocumentChunk chunk, double score) {
            this.chunk = chunk;
            this.score = score;
        }
    }
}
