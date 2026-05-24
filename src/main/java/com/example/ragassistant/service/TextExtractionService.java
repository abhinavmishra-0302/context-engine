package com.example.ragassistant.service;

import com.example.ragassistant.exception.AppException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TextExtractionService {

    public String extract(Path filePath, String originalFileName) {
        String safeName = originalFileName == null ? "" : originalFileName;
        String lower = safeName.toLowerCase();
        try {
            if (lower.endsWith(".pdf") || isPdf(filePath)) {
                try (var document = Loader.loadPDF(filePath.toFile())) {
                    return new PDFTextStripper().getText(document);
                }
            }
            if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".csv")) {
                return Files.readString(filePath, StandardCharsets.UTF_8);
            }
            // Extensionless/unknown text files: attempt UTF-8 read.
            return Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.error("Failed to extract text from {}", filePath, ex);
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to parse uploaded document");
        }
    }

    private boolean isPdf(Path filePath) {
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            byte[] header = new byte[5];
            int read = inputStream.read(header);
            if (read < 5) {
                return false;
            }
            byte[] pdfHeader = "%PDF-".getBytes(StandardCharsets.US_ASCII);
            return Arrays.equals(header, pdfHeader);
        } catch (IOException ex) {
            return false;
        }
    }
}
