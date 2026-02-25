package com.messenger.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();

        // Маппинг для аватарок
        registry.addResourceHandler("/avatars/**")
                .addResourceLocations("file:" + uploadPath.resolve("avatars") + "/");

        // Маппинг для других загрузок, если понадобится
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath + "/");

        // Временное решение для отладки - маппим общий путь
        System.out.println("Configured resource handler for avatars: " + uploadPath.resolve("avatars"));
    }
}
