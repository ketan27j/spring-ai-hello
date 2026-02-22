package com.example.springaihello.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class ExternalApiTools {

    private static final Logger logger = LoggerFactory.getLogger(ExternalApiTools.class);
    private final RestClient restClient;

    public ExternalApiTools() {
        this.restClient = RestClient.builder().build();
    }

    @Tool(description = "Call an external RAG service for additional knowledge")
    public String callExternalRag(
            @ToolParam(description = "The query to send to the external RAG service") String query) {
        try {
            logger.info("Calling external RAG service with query: {}", query);

            // For now, this is a placeholder passthrough logic.
            // In a real scenario, this would call a specific endpoint.
            // String response = restClient.post()
            // .uri("https://api.external-rag.com/v1/search")
            // .body(new QueryRequest(query))
            // .retrieve()
            // .body(String.class);

            // Mocking a successful external call response
            return "External RAG Response: Knowledge retrieved for '" + query
                    + "' from external source. [Placeholder Content]";
        } catch (Exception e) {
            logger.error("Error calling external RAG service", e);
            return "Error calling external RAG service: " + e.getMessage();
        }
    }
}
