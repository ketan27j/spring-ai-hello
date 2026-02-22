package com.example.springaihello.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MySqlMcpTools {

    private static final Logger logger = LoggerFactory.getLogger(MySqlMcpTools.class);
    private final JdbcTemplate jdbcTemplate;

    public MySqlMcpTools(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Tool(description = "Get fees status (pending/paid) for a strategy by its strategy code")
    public String getFeesStatusByStrategyCode(
            @ToolParam(description = "The unique strategy code to look up") String strategyCode) {
        logger.info("MCP Tool called: getFeesStatusByStrategyCode with strategyCode={}", strategyCode);
        try {
            String sql = "SELECT * FROM strategy_fees WHERE strategy_code = ?";
            logger.debug("Executing SQL: {} with param: {}", sql, strategyCode);
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, strategyCode);

            if (results.isEmpty()) {
                logger.warn("No record found for strategy code: {}", strategyCode);
                return "No record found for strategy code: " + strategyCode;
            }

            logger.info("Found {} records for strategy code: {}", results.size(), strategyCode);

            Map<String, Object> row = results.get(0);
            StringBuilder result = new StringBuilder();
            result.append("Strategy Code: ").append(row.get("strategy_code")).append("\n");
            result.append("Fees Status: ").append(row.get("fees_status")).append("\n");

            if (row.containsKey("amount")) {
                result.append("Amount: ").append(row.get("amount")).append("\n");
            }
            if (row.containsKey("created_at")) {
                result.append("Created At: ").append(row.get("created_at")).append("\n");
            }
            if (row.containsKey("updated_at")) {
                result.append("Updated At: ").append(row.get("updated_at")).append("\n");
            }

            return result.toString();
        } catch (Exception e) {
            logger.error("Error querying database for strategy code {}: {}", strategyCode, e.getMessage(), e);
            return "Error querying database: " + e.getMessage();
        }
    }
}