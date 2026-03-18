package com.messenger.core.controller;

import com.messenger.core.dto.MessageDto;
import com.messenger.core.service.chat.ChatAccessService;
import com.messenger.core.service.FileStorageService;
import com.messenger.core.service.message.MessageService;
import com.messenger.core.service.user.UserContextResolver;
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
    private final ChatAccessService chatAccessService;
    private final UserContextResolver userContextResolver;

    /**
     * Загрузить файл в чат
     */
    @PostMapping("/upload")
    public ResponseEntity<MessageDto> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("chatId") Long chatId,
            @RequestParam(value = "caption", required = false) String caption,
            HttpServletRequest request) throws IOException {

        Long userId = getCurrentUserId(request);
        log.info("Uploading file: {} to chat: {} by user: {}", file.getOriginalFilename(), chatId, userId);

        chatAccessService.verifyParticipant(chatId, userId);

        FileStorageService.FileInfo fileInfo = fileStorageService.storeFile(file, chatId);
        MessageDto message = messageService.sendFileMessage(userId, chatId, caption, fileInfo);

        log.info("File uploaded successfully: {}", fileInfo.getFileUrl());
        return ResponseEntity.ok(message);
    }

    /**
     * Скачать файл
     */
    @GetMapping("/{chatFolder}/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String chatFolder,
            @PathVariable String fileName,
            @RequestParam(value = "token", required = false) String tokenParam,
            HttpServletRequest request) throws IOException {

        Long userId = getCurrentUserId(request, tokenParam);
        Long chatId = Long.parseLong(chatFolder.replace("chat_", ""));

        chatAccessService.verifyParticipant(chatId, userId);

        Path filePath = fileStorageService.loadFile(chatFolder, fileName);
        Resource resource = new UrlResource(filePath.toUri());

        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }

        String contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    private Long getCurrentUserId(HttpServletRequest request) {
        return userContextResolver.resolveUserId(request);
    }

    private Long getCurrentUserId(HttpServletRequest request, String tokenParam) {
        return userContextResolver.resolveUserId(request, tokenParam);
    }
}
