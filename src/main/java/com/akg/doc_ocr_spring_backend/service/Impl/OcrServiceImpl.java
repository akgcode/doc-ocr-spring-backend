package com.akg.doc_ocr_spring_backend.service.Impl;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.akg.doc_ocr_spring_backend.service.OcrService;

import java.io.IOException;

@Service
public class OcrServiceImpl implements OcrService {

    /**
     * Placeholder PDF processing method. Replace with real OCR logic.
     */
    @Override
    public String processPdf(MultipartFile file) throws IOException {
        // For now, just return a placeholder string including file name and size.
        String name = file.getOriginalFilename();
        long size = file.getSize();
        return "[placeholder OCR text] file='" + (name == null ? "unknown" : name) + "' size=" + size + " bytes";
    }

}