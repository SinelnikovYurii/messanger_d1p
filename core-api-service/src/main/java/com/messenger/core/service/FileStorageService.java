package com.messenger.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    @Value("${file.upload.dir}")
    private String uploadDir;

    @Value("${file.upload.max-size}")
    private long maxFileSize;

    @Value("${file.upload.allowed-types}")
    private String allowedTypes;

    private static final List<String> IMAGE_TYPES = Arrays.asList(
        "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    private static final int THUMBNAIL_WIDTH = 300;
    private static final int THUMBNAIL_HEIGHT = 300;

    /**
     * Сохранить файл и вернуть информацию о нем
     */
    public FileInfo storeFile(MultipartFile file, Long chatId) throws IOException {
        validateFile(file);

        // Создаем директорию для чата если её нет
        Path chatDir = Paths.get(uploadDir, "chat_" + chatId);
        Files.createDirectories(chatDir);

        // Генерируем уникальное имя файла
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null) {
            throw new IllegalArgumentException("Имя файла не может быть пустым");
        }
        originalFileName = StringUtils.cleanPath(originalFileName);
        String fileExtension = getFileExtension(originalFileName);
        String storedFileName = UUID.randomUUID() + fileExtension;

        // Сохраняем файл
        Path targetLocation = chatDir.resolve(storedFileName);
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

        FileInfo fileInfo = new FileInfo();
        fileInfo.setFileName(originalFileName);
        fileInfo.setStoredFileName(storedFileName);
        fileInfo.setFileUrl("/api/files/chat_" + chatId + "/" + storedFileName);
        fileInfo.setFileSize(file.getSize());
        fileInfo.setMimeType(file.getContentType());

        // Создаем превью для изображений
        if (isImage(file.getContentType())) {
            try {
                String thumbnailName = createThumbnail(targetLocation, chatDir);
                fileInfo.setThumbnailUrl("/api/files/chat_" + chatId + "/" + thumbnailName);
            } catch (Exception e) {
                log.error("Failed to create thumbnail for {}: {}", originalFileName, e.getMessage());
                // Продолжаем без превью
            }
        }

        log.info("File stored successfully: {} -> {}", originalFileName, storedFileName);
        return fileInfo;
    }

    /**
     * Создать миниатюру для изображения
     */
    private String createThumbnail(Path originalFile, Path chatDir) throws IOException {
        BufferedImage originalImage = ImageIO.read(originalFile.toFile());

        if (originalImage == null) {
            throw new IOException("Cannot read image file");
        }

        // Вычисляем размеры с сохранением пропорций
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();

        double aspectRatio = (double) width / height;
        int targetWidth = THUMBNAIL_WIDTH;
        int targetHeight = THUMBNAIL_HEIGHT;

        if (aspectRatio > 1) {
            targetHeight = (int) (targetWidth / aspectRatio);
        } else {
            targetWidth = (int) (targetHeight * aspectRatio);
        }

        // Создаем миниатюру
        BufferedImage thumbnail = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = thumbnail.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        g.dispose();

        // Сохраняем миниатюру
        String thumbnailName = "thumb_" + originalFile.getFileName().toString();
        Path thumbnailPath = chatDir.resolve(thumbnailName);
        ImageIO.write(thumbnail, "jpg", thumbnailPath.toFile());

        return thumbnailName;
    }

    /**
     * Получить файл по пути
     */
    public Path loadFile(String chatFolder, String fileName) throws IOException {
        Path filePath = Paths.get(uploadDir, chatFolder, fileName).normalize();

        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + fileName);
        }

        return filePath;
    }

    /**
     * Удалить файл
     */
    public void deleteFile(String fileUrl) {
        try {
            // Извлекаем путь из URL
            String path = fileUrl.replace("/api/files/", "");
            Path filePath = Paths.get(uploadDir, path).normalize();

            Files.deleteIfExists(filePath);

            // Удаляем превью если есть
            String fileName = filePath.getFileName().toString();
            if (!fileName.startsWith("thumb_")) {
                Path thumbnailPath = filePath.getParent().resolve("thumb_" + fileName);
                Files.deleteIfExists(thumbnailPath);
            }

            log.info("File deleted: {}", path);
        } catch (Exception e) {
            log.error("Failed to delete file {}: {}", fileUrl, e.getMessage());
        }
    }

    /**
     * Валидация файла
     */
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Файл пустой");
        }

        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("Размер файла превышает максимально допустимый: " + (maxFileSize / 1024 / 1024) + "MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !isAllowedType(contentType)) {
            throw new IllegalArgumentException("Недопустимый тип файла: " + contentType);
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.contains("..")) {
            throw new IllegalArgumentException("Недопустимое имя файла");
        }
    }

    /**
     * Проверка разрешенного типа файла
     */
    private boolean isAllowedType(String contentType) {

        return contentType != null && !contentType.trim().isEmpty();

    }

    /**
     * Проверка, является ли файл изображением
     */
    private boolean isImage(String contentType) {
        return contentType != null && IMAGE_TYPES.contains(contentType);
    }

    /**
     * Получить расширение файла
     */
    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex);
    }

    /**
     * DTO с информацией о файле
     */
    public static class FileInfo {
        private String fileName;
        private String storedFileName;
        private String fileUrl;
        private Long fileSize;
        private String mimeType;
        private String thumbnailUrl;

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public String getStoredFileName() { return storedFileName; }
        public void setStoredFileName(String storedFileName) { this.storedFileName = storedFileName; }

        public String getFileUrl() { return fileUrl; }
        public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

        public Long getFileSize() { return fileSize; }
        public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }

        public String getThumbnailUrl() { return thumbnailUrl; }
        public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    }
}

