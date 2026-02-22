package com.example.springaihello.mcp;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MySqlMcpTools {

    private final JdbcTemplate jdbcTemplate;

    public MySqlMcpTools(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Tool(description = "Get fees status (pending/paid) for a strategy by its ddddd strategy code")
    public String getFeesStatusByStrategyCode(
            @ToolParam(description = "The unique strategy code to look up") String strategyCode) {
        try {
            String sql = "SELECT * FROM strategy_fees WHERE strategy_code = ?";
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, strategyCode);
            
            if (results.isEmpty()) {
                return "No record found for strategy code: " + strategyCode;
            }
            
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
            return "Error querying database: " + e.getMessage();
        }
    }

    @Tool(description = "List all strategies with their fees status")
    public String listAllStrategies() {
        try {
            String sql = "SELECT strategy_code, fees_status FROM strategy_fees LIMIT 100";
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
            
            if (results.isEmpty()) {
                return "No strategy records found in database";
            }
            
            StringBuilder result = new StringBuilder("Strategy List:\n");
            for (Map<String, Object> row : results) {
                result.append("- ").append(row.get("strategy_code"))
                      .append(": ").append(row.get("fees_status")).append("\n");
            }
            return result.toString();
        } catch (Exception e) {
            return "Error querying database: " + e.getMessage();
        }
    }
}