package com.example.springaihello.vector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class MySqlVectorStore implements VectorStore {

    private static final Logger logger = LoggerFactory.getLogger(MySqlVectorStore.class);

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;

    public MySqlVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
        this.objectMapper = new ObjectMapper();
        initializeSchema();
    }

    private void initializeSchema() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS vector_documents (
                id VARCHAR(36) PRIMARY KEY,
                content TEXT NOT NULL,
                embedding JSON NOT NULL,
                metadata JSON,
                FULLTEXT KEY ft_content (content)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS document_stats (
                id INT PRIMARY KEY DEFAULT 1,
                total_documents INT DEFAULT 0,
                avg_document_length FLOAT DEFAULT 0
            )
        """);

        jdbcTemplate.update("INSERT IGNORE INTO document_stats VALUES (1, 0, 0)");
    }

    @Override
    public void add(List<Document> documents) {
        documents.forEach(doc -> {
            float[] embedding = embeddingModel.embed(doc);
            try {
                String embeddingJson = objectMapper.writeValueAsString(embedding);
                String metadataJson = objectMapper.writeValueAsString(doc.getMetadata());

                KeyHolder keyHolder = new GeneratedKeyHolder();
                jdbcTemplate.update(conn -> {
                    PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO vector_documents (id, content, embedding, metadata)
                        VALUES (?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE content=?, embedding=?, metadata=?
                    """, Statement.RETURN_GENERATED_KEYS);
                    ps.setString(1, doc.getId());
                    ps.setString(2, doc.getText());
                    ps.setString(3, embeddingJson);
                    ps.setString(4, metadataJson);
                    ps.setString(5, doc.getText());
                    ps.setString(6, embeddingJson);
                    ps.setString(7, metadataJson);
                    return ps;
                }, keyHolder);

            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize vector data", e);
            }
        });

        updateDocumentStats();
    }

    @Override
    public void delete(Filter.Expression filterExpression) {
        // Filter support not implemented for simplicity
    }

    @Override
    public void delete(List<String> idList) {
        jdbcTemplate.batchUpdate("DELETE FROM vector_documents WHERE id = ?",
                idList.stream().map(id -> new Object[]{id}).toList());

        updateDocumentStats();
    }

    @Override
    public List<Document> similaritySearch(SearchRequest searchRequest) {
        logger.info("🔍 Hybrid Search Request: query='{}', topK={}, threshold={}",
                searchRequest.getQuery(), searchRequest.getTopK(), searchRequest.getSimilarityThreshold());

        float[] queryEmbedding = embeddingModel.embed(searchRequest.getQuery());

        // Hybrid BM25 + Vector search strategy
        List<SearchResult> bm25Results = searchBM25(searchRequest.getQuery(), searchRequest.getTopK() * 2);
        List<SearchResult> vectorResults = searchVectorSimilarity(queryEmbedding, searchRequest.getTopK() * 2);

        logger.info("📊 BM25 Search returned {} results", bm25Results.size());
        bm25Results.forEach(r -> logger.debug("  BM25: [score={:.4f}] {}", r.score,
                r.document.getText().substring(0, Math.min(r.document.getText().length(), 100))));

        logger.info("📊 Vector Search returned {} results", vectorResults.size());
        vectorResults.forEach(r -> logger.debug("  Vector: [score={:.4f}] {}", r.score,
                r.document.getText().substring(0, Math.min(r.document.getText().length(), 100))));

        // Combine and rank results using reciprocal rank fusion
        Map<String, SearchResult> combined = new HashMap<>();

        for (int i = 0; i < bm25Results.size(); i++) {
            SearchResult res = bm25Results.get(i);
            res.score = 1.0 / (i + 60); // RRF with k=60
            combined.put(res.document.getId(), res);
        }

        for (int i = 0; i < vectorResults.size(); i++) {
            SearchResult res = vectorResults.get(i);
            double vectorScore = 1.0 / (i + 60);
            if (combined.containsKey(res.document.getId())) {
                combined.get(res.document.getId()).score += vectorScore;
            } else {
                res.score = vectorScore;
                combined.put(res.document.getId(), res);
            }
        }

        List<SearchResult> finalResults = combined.values().stream()
                .sorted(Comparator.comparingDouble(r -> -r.score))
                .limit(searchRequest.getTopK())
                .filter(r -> r.score > searchRequest.getSimilarityThreshold() / 10)
                .collect(Collectors.toList());

        logger.info("✅ Final Merged Results: {} documents returned", finalResults.size());
        finalResults.forEach(r -> logger.info("  📄 [RRF score={:.4f}] {}", r.score,
                r.document.getText().substring(0, Math.min(r.document.getText().length(), 150))));

        return finalResults.stream()
                .map(r -> r.document)
                .collect(Collectors.toList());
    }

    private List<SearchResult> searchBM25(String query, int limit) {
        return jdbcTemplate.query("""
                SELECT id, content, metadata,
                    MATCH(content) AGAINST (? IN NATURAL LANGUAGE MODE) as bm25_score
                FROM vector_documents
                WHERE MATCH(content) AGAINST (? IN NATURAL LANGUAGE MODE) > 0
                ORDER BY bm25_score DESC
                LIMIT ?
            """, (rs, row) -> {
                Document doc = new Document(rs.getString("id"), rs.getString("content"), parseMetadata(rs.getString("metadata")));
                return new SearchResult(doc, rs.getDouble("bm25_score"));
            }, query, query, limit);
    }

    private List<SearchResult> searchVectorSimilarity(float[] queryEmbedding, int limit) {
        try {
            String queryEmbeddingJson = objectMapper.writeValueAsString(queryEmbedding);

            return jdbcTemplate.query("""
                    SELECT id, content, metadata, embedding,
                        (1 - JSON_DOT_PRODUCT(embedding, ?) / (JSON_LENGTH(embedding) * JSON_LENGTH(?))) as cosine_distance
                    FROM vector_documents
                    ORDER BY cosine_distance ASC
                    LIMIT ?
                """, (rs, row) -> {
                    Document doc = new Document(rs.getString("id"), rs.getString("content"), parseMetadata(rs.getString("metadata")));
                    double similarity = 1.0 - rs.getDouble("cosine_distance");
                    return new SearchResult(doc, similarity);
                }, queryEmbeddingJson, queryEmbeddingJson, limit);

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to process query embedding", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetadata(String metadataJson) {
        try {
            return objectMapper.readValue(metadataJson, Map.class);
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }

    private void updateDocumentStats() {
        jdbcTemplate.update("""
            UPDATE document_stats
            SET total_documents = (SELECT COUNT(*) FROM vector_documents),
                avg_document_length = (SELECT AVG(LENGTH(content)) FROM vector_documents)
        """);
    }

    private static class SearchResult {
        Document document;
        double score;

        SearchResult(Document document, double score) {
            this.document = document;
            this.score = score;
        }
    }
}