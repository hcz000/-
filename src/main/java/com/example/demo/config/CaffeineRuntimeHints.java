package com.example.demo.config;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Native Image runtime hints for Caffeine cache
 */
@Configuration
public class CaffeineRuntimeHints {

    @Bean
    public RuntimeHintsRegistrar caffeineRuntimeHintsRegistrar() {
        return new RuntimeHintsRegistrar() {
            @Override
            public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
                // Register Caffeine cache implementation classes for reflection with full access
                // Caffeine uses runtime class loading to select the best cache implementation
                // All these classes need to be accessible at runtime for Native Image

                String[] cacheClasses = {
                    // Strong strong no stats no async
                    "com.github.benmanes.caffeine.cache.SSMSA",
                    "com.github.benmanes.caffeine.cache.SSMSAW",
                    // Strong strong stats no async
                    "com.github.benmanes.caffeine.cache.PSMSA",
                    "com.github.benmanes.caffeine.cache.PSMSAW",
                    // Strong strong stats size async
                    "com.github.benmanes.caffeine.cache.MSMSA",
                    "com.github.benmanes.caffeine.cache.MSMSAW",
                    // PSA - strong strong no stats async
                    "com.github.benmanes.caffeine.cache.PSA",
                    "com.github.benmanes.caffeine.cache.PSAW",
                    // SSA - strong strong stats async
                    "com.github.benmanes.caffeine.cache.SSA",
                    "com.github.benmanes.caffeine.cache.SSAW",
                    // MSA - strong strong stats size async
                    "com.github.benmanes.caffeine.cache.MSA",
                    "com.github.benmanes.caffeine.cache.MSAW",
                    // Strong weak no stats no async
                    "com.github.benmanes.caffeine.cache.SWMSA",
                    "com.github.benmanes.caffeine.cache.SWMSAW",
                    // Strong weak stats no async
                    "com.github.benmanes.caffeine.cache.PWMSA",
                    "com.github.benmanes.caffeine.cache.PWMSAW",
                    // Strong weak stats size async
                    "com.github.benmanes.caffeine.cache.MWMSA",
                    "com.github.benmanes.caffeine.cache.MWMSAW",
                    // PWSA - strong weak no stats async
                    "com.github.benmanes.caffeine.cache.PWSA",
                    "com.github.benmanes.caffeine.cache.PWSAW",
                    // SWSA - strong weak stats async
                    "com.github.benmanes.caffeine.cache.SWSA",
                    "com.github.benmanes.caffeine.cache.SWSAW",
                    // MWSA - strong weak stats size async
                    "com.github.benmanes.caffeine.cache.MWSA",
                    "com.github.benmanes.caffeine.cache.MWSAW",
                    // LocalCacheFactory
                    "com.github.benmanes.caffeine.cache.LocalCacheFactory"
                };

                for (String className : cacheClasses) {
                    hints.reflection().registerType(
                        org.springframework.aot.hint.TypeReference.of(className),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.INVOKE_PUBLIC_METHODS,
                        MemberCategory.DECLARED_FIELDS,
                        MemberCategory.PUBLIC_FIELDS
                    );
                }

                // Also register the BoundedLocalCache and its inner classes
                hints.reflection().registerType(
                    org.springframework.aot.hint.TypeReference.of("com.github.benmanes.caffeine.cache.BoundedLocalCache"),
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.DECLARED_FIELDS,
                    MemberCategory.PUBLIC_FIELDS
                );

                // Register inner cache classes
                String[] innerClasses = {
                    "com.github.benmanes.caffeine.cache.BoundedLocalCache$BoundedLocalManualCache",
                    "com.github.benmanes.caffeine.cache.BoundedLocalCache$BoundedLocalLoadingCache",
                    "com.github.benmanes.caffeine.cache.BoundedLocalCache$BoundedLocalAsyncCache",
                    "com.github.benmanes.caffeine.cache.BoundedLocalCache$BoundedLocalAsyncLoadingCache"
                };

                for (String className : innerClasses) {
                    hints.reflection().registerType(
                        org.springframework.aot.hint.TypeReference.of(className),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.INVOKE_PUBLIC_METHODS
                    );
                }
            }
        };
    }
}