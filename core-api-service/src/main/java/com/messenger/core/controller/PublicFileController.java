package com.messenger.core.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Контроллер для публичного доступа к статическим файлам (аватарки)
 * Не требует JWT авторизации
 */
@RestController
@RequestMapping
@Slf4j
public class PublicFileController {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    /**
     * Публичный endpoint для получения аватарок
     * Доступен без JWT токена
     */
    @GetMapping("/avatars/{filename:.+}")
    public ResponseEntity<Resource> getAvatar(@PathVariable String filename) {
        try {
            log.info("Запрос аватарки: {}", filename);

            Path filePath = Paths.get(uploadDir, "avatars", filename);
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                log.warn("Аватарка не найдена или недоступна: {}", filename);
                return ResponseEntity.notFound().build();
            }

            // Определяем MIME тип
            String contentType = "image/jpeg"; // По умолчанию
            if (filename.toLowerCase().endsWith(".png")) {
                contentType = "image/png";
            } else if (filename.toLowerCase().endsWith(".gif")) {
                contentType = "image/gif";
            } else if (filename.toLowerCase().endsWith(".webp")) {
                contentType = "image/webp";
            }

            log.info("Аватарка успешно найдена: {}, тип: {}", filename, contentType);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000") // Кеширование на 1 год
                    .body(resource);

        } catch (IOException e) {
            log.error("Ошибка при чтении аватарки {}: {}", filename, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Альтернативный путь для совместимости
     */
    @GetMapping("/uploads/avatars/{filename:.+}")
    public ResponseEntity<Resource> getAvatarAlternative(@PathVariable String filename) {
        return getAvatar(filename);
    }
}

