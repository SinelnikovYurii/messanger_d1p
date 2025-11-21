package com.messenger.core.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileStorageServiceTest {
    @InjectMocks
    private FileStorageService fileStorageService;

    @Mock
    private MultipartFile multipartFile;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(fileStorageService, "uploadDir", "uploads_test");
        ReflectionTestUtils.setField(fileStorageService, "maxFileSize", 1024 * 1024);
        ReflectionTestUtils.setField(fileStorageService, "allowedTypes", "image/jpeg,image/png,application/pdf");
    }

    @Test
    void testValidateFile_emptyFile() {
        when(multipartFile.isEmpty()).thenReturn(true);
        Exception ex = assertThrows(IllegalArgumentException.class, () -> fileStorageService.storeFile(multipartFile, 1L));
        assertTrue(ex.getMessage().contains("Файл пустой"));
    }

    @Test
    void testValidateFile_largeFile() {
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getSize()).thenReturn(2L * 1024 * 1024);
        when(multipartFile.getContentType()).thenReturn("image/jpeg");
        when(multipartFile.getOriginalFilename()).thenReturn("test.jpg");
        Exception ex = assertThrows(IllegalArgumentException.class, () -> fileStorageService.storeFile(multipartFile, 1L));
        assertTrue(ex.getMessage().contains("Размер файла превышает"));
    }

    @Test
    void testValidateFile_invalidType() {
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getSize()).thenReturn(100L);
        when(multipartFile.getContentType()).thenReturn("application/zip");
        when(multipartFile.getOriginalFilename()).thenReturn("test.zip");
        Exception ex = assertThrows(IllegalArgumentException.class, () -> fileStorageService.storeFile(multipartFile, 1L));
        assertTrue(ex.getMessage().contains("Недопустимый тип файла"));
    }

    @Test
    void testValidateFile_invalidName() {
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getSize()).thenReturn(100L);
        when(multipartFile.getContentType()).thenReturn("image/jpeg");
        when(multipartFile.getOriginalFilename()).thenReturn("..\ntest.jpg");
        Exception ex = assertThrows(IllegalArgumentException.class, () -> fileStorageService.storeFile(multipartFile, 1L));
        assertTrue(ex.getMessage().contains("Недопустимое имя файла"));
    }

    @Test
    void testStoreFile_success() throws IOException {
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getSize()).thenReturn(100L);
        when(multipartFile.getContentType()).thenReturn("image/jpeg");
        when(multipartFile.getOriginalFilename()).thenReturn("test.jpg");
        when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[100]));
        // createThumbnail будет выброшено исключение, но это допустимо
        FileStorageService.FileInfo info = fileStorageService.storeFile(multipartFile, 1L);
        assertNotNull(info);
        assertEquals("test.jpg", info.getFileName());
        assertTrue(info.getFileUrl().contains("chat_1"));
    }

    @Test
    void testLoadFile_notFound() {
        Exception ex = assertThrows(IOException.class, () -> fileStorageService.loadFile("chat_1", UUID.randomUUID() + ".jpg"));
        assertTrue(ex.getMessage().contains("File not found"));
    }

    @Test
    void testDeleteFile_noException() {
        String fileUrl = "/api/files/chat_1/test.jpg";
        // Файл не существует, но метод должен отработать без исключения
        assertDoesNotThrow(() -> fileStorageService.deleteFile(fileUrl));
    }
}

