package com.example.ragassistant.controller;

import com.example.ragassistant.dto.DocumentResponse;
import com.example.ragassistant.dto.DocumentUploadResponse;
import com.example.ragassistant.service.DocumentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/upload")
    public DocumentUploadResponse upload(@RequestPart("file") MultipartFile file) {
        return documentService.upload(file);
    }

    @GetMapping
    public List<DocumentResponse> list() {
        return documentService.listCurrentUserDocuments();
    }
}
