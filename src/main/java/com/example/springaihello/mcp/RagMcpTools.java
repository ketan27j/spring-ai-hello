package com.example.springaihello.mcp;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagMcpTools {

    private static final Logger logger = LoggerFactory.getLogger(RagMcpTools.class);
    private final SimpleVectorStore vectorStore;
    private final EmbeddingModel embeddingModel;

    public RagMcpTools(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        this.vectorStore = SimpleVectorStore.builder(embeddingModel).build();
    }

    @PostConstruct
    public void init() {
        try {
            // Check if there are any PDFs to ingest
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:/data/*.pdf");

            if (resources.length == 0) {
                logger.warn("No PDF documents found in src/main/resources/data/ for ingestion.");
                return;
            }

            for (Resource resource : resources) {
                logger.info("Ingesting PDF: {}", resource.getFilename());
                PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource,
                        PdfDocumentReaderConfig.builder()
                                .build());

                TokenTextSplitter textSplitter = new TokenTextSplitter();
                List<Document> documents = textSplitter.apply(pdfReader.get());
                vectorStore.accept(documents);
                logger.info("Successfully ingested {} chunks from {}", documents.size(), resource.getFilename());
            }
        } catch (IOException e) {
            logger.error("Error ingesting PDF documents", e);
        }
    }

    @Tool(description = "Search through uploaded PDF documents for relevant information")
    public String searchDocuments(
            @ToolParam(description = "The search query or question to find information for") String query) {
        try {
            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(5)
                            .similarityThreshold(0.7)
                            .build());

            if (results.isEmpty()) {
                return "No relevant information found in the documents for your query.";
            }

            return results.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n---\n"));
        } catch (Exception e) {
            logger.error("Error during similarity search", e);
            return "Error searching documents: " + e.getMessage();
        }
    }
}
