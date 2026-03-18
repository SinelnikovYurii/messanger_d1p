package com.messenger.core.controller;

import com.messenger.core.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.NoSuchFileException;

/**
 * Контроллер для публичного доступа к статическим файлам (аватарки).
 * Не требует JWT авторизации.
 * Ответственность: маршрутизация HTTP-запросов и формирование ответа (SRP).
 * Вся файловая логика делегирована в {@link FileStorageService} (DIP).
 */
@RestController
@RequestMapping
@Slf4j
@RequiredArgsConstructor
public class PublicFileController {

    private final FileStorageService fileStorageService;

    /**
     * Публичный endpoint для получения аватарок.
     * Доступен без JWT токена.
     * Поддерживает два пути для совместимости: /avatars/... и /uploads/avatars/...
     */
    @GetMapping({"/avatars/{filename:.+}", "/uploads/avatars/{filename:.+}"})
    public ResponseEntity<Resource> getAvatar(@PathVariable String filename) {
        log.info("Запрос аватарки: {}", filename);
        try {
            Resource resource = fileStorageService.loadAvatar(filename);
            String contentType = fileStorageService.detectMimeType(filename);
            log.info("Аватарка успешно найдена: {}, тип: {}", filename, contentType);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000")
                    .body(resource);
        } catch (NoSuchFileException e) {
            log.warn("Аватарка не найдена: {}", filename);
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            log.error("Ошибка при чтении аватарки {}: {}", filename, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
