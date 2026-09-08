package com.moneykk.moneytown.analysis.global.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatConfig {

    @Bean
    public ChatClient portfolioChatClient(ChatClient.Builder builder){
        return builder.build();
    }
}
