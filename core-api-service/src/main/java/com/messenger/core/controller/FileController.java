package com.messenger.core.controller;

import com.messenger.core.config.JwtAuthenticationFilter;
import com.messenger.core.dto.MessageDto;
import com.messenger.core.repository.ChatRepository;
import com.messenger.core.service.FileStorageService;
import com.messenger.core.service.MessageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {

    private final FileStorageService fileStorageService;
    private final MessageService messageService;
    private final ChatRepository chatRepository;

    /**
     * Загрузить файл в чат
     */
    @PostMapping("/upload")
    public ResponseEntity<MessageDto> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("chatId") Long chatId,
            @RequestParam(value = "caption", required = false) String caption,
            HttpServletRequest request) {

        try {
            Long userId = getCurrentUserId(request);

            log.info("Uploading file: {} to chat: {} by user: {}", file.getOriginalFilename(), chatId, userId);

            // Проверяем, что пользователь является участником чата
            if (!chatRepository.isUserParticipant(chatId, userId)) {
                return ResponseEntity.status(403).build();
            }

            // Сохраняем файл
            FileStorageService.FileInfo fileInfo = fileStorageService.storeFile(file, chatId);

            // Создаем сообщение с файлом
            MessageDto.SendMessageRequest messageRequest = new MessageDto.SendMessageRequest();
            messageRequest.setChatId(chatId);
            messageRequest.setContent(caption != null ? caption : fileInfo.getFileName());
            messageRequest.setFileUrl(fileInfo.getFileUrl());
            messageRequest.setFileName(fileInfo.getFileName());
            messageRequest.setFileSize(fileInfo.getFileSize());
            messageRequest.setMimeType(fileInfo.getMimeType());
            messageRequest.setThumbnailUrl(fileInfo.getThumbnailUrl());

            // Определяем тип сообщения
            if (fileInfo.getMimeType().startsWith("image/")) {
                messageRequest.setMessageType(com.messenger.core.model.Message.MessageType.IMAGE);
            } else {
                messageRequest.setMessageType(com.messenger.core.model.Message.MessageType.FILE);
            }

            MessageDto message = messageService.sendMessage(userId, messageRequest);

            log.info("File uploaded successfully: {}", fileInfo.getFileUrl());
            return ResponseEntity.ok(message);

        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            log.error("IO error while uploading file: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Скачать файл
     */
    @GetMapping("/{chatFolder}/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String chatFolder,
            @PathVariable String fileName,
            @RequestParam(value = "token", required = false) String tokenParam,
            HttpServletRequest request) {

        try {
            Long userId = getCurrentUserId(request, tokenParam);

            // Извлекаем chatId из папки (формат: chat_123)
            Long chatId = Long.parseLong(chatFolder.replace("chat_", ""));

            // Проверяем доступ
            if (!chatRepository.isUserParticipant(chatId, userId)) {
                return ResponseEntity.status(403).build();
            }

            // Загружаем файл
            Path filePath = fileStorageService.loadFile(chatFolder, fileName);
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            // Определяем тип контента
            String contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);

        } catch (NumberFormatException e) {
            log.error("Invalid chat folder format: {}", chatFolder);
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            log.error("Error loading file: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Получить ID текущего пользователя
     */
    private Long getCurrentUserId(HttpServletRequest request) {
        return getCurrentUserId(request, null);
    }

    /**
     * Получить ID текущего пользователя (с поддержкой токена из query параметров)
     */
    private Long getCurrentUserId(HttpServletRequest request, String tokenParam) {
        // Сначала проверяем заголовок X-User-Id (от Gateway)
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader != null && !userIdHeader.isEmpty()) {
            try {
                return Long.parseLong(userIdHeader);
            } catch (NumberFormatException e) {
                throw new RuntimeException("Неверный формат ID пользователя в заголовке: " + userIdHeader);
            }
        }

        // Если есть токен в query параметрах (для GET запросов изображений)
        if (tokenParam != null && !tokenParam.isEmpty()) {
            try {
                // Здесь нужно декодировать токен и извлечь userId
                // Для простоты используем заголовок, который Gateway должен добавить
                log.warn("Token from query param, but X-User-Id header is missing. Token: {}", tokenParam.substring(0, Math.min(10, tokenParam.length())));
            } catch (Exception e) {
                log.error("Error processing token from query: {}", e.getMessage());
            }
        }

        throw new RuntimeException("Не удалось получить ID пользователя");
    }
}
