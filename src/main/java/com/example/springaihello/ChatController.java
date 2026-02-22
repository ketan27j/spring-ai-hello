package com.example.springaihello;

import com.example.springaihello.mcp.MySqlMcpTools;
import com.example.springaihello.mcp.RagMcpTools;
import com.example.springaihello.mcp.ExternalApiTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final ChatClient chatClient;
    private final MySqlMcpTools mySqlMcpTools;
    private final RagMcpTools ragMcpTools;
    private final ExternalApiTools externalApiTools;

    public ChatController(ChatClient.Builder builder,
            MySqlMcpTools mySqlMcpTools,
            RagMcpTools ragMcpTools,
            ExternalApiTools externalApiTools) {
        super();
        this.mySqlMcpTools = mySqlMcpTools;
        this.ragMcpTools = ragMcpTools;
        this.externalApiTools = externalApiTools;
        this.chatClient = builder
                .defaultTools(mySqlMcpTools, ragMcpTools, externalApiTools)
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                .build();
    }

    @GetMapping("/ask")
    public String ask(@RequestParam(defaultValue = "Say Hello World!") String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}
