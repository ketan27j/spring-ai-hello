package com.example.springaihello;

import com.example.springaihello.mcp.MySqlMcpTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final ChatClient chatClient;
    private final MySqlMcpTools mySqlMcpTools;

    public ChatController(ChatClient.Builder builder, MySqlMcpTools mySqlMcpTools) {
        this.mySqlMcpTools = mySqlMcpTools;
        this.chatClient = builder
                .defaultTools(mySqlMcpTools)
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
