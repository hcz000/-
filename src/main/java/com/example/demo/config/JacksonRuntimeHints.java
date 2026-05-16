package com.example.demo.config;

import com.example.demo.entity.dto.ChatMessageResponse;
import com.example.demo.entity.dto.ChatMessageSendRequest;
import com.example.demo.entity.dto.FriendSummary;
import com.example.demo.entity.dto.NotificationResponse;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(JacksonRuntimeHints.Registrar.class)
public class JacksonRuntimeHints {

    static class Registrar implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            BindingReflectionHintsRegistrar registrar = new BindingReflectionHintsRegistrar();
            registrar.registerReflectionHints(
                    hints.reflection(),
                    FriendSummary.class,
                    ChatMessageResponse.class,
                    NotificationResponse.class,
                    ChatMessageSendRequest.class
            );
        }
    }
}
