package com.akg.doc_ocr_spring_backend.service;

import com.akg.doc_ocr_spring_backend.dto.OcrSpaceResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface OcrService {

    /**
     * Processes a PDF file using the OCR Space API.
     */
    String processPdf(MultipartFile file) throws IOException;

    /**
     * Parses the OCR response (as a Map) and extracts text from ParsedResults array.
     */
    List<String> parsePdf(Map<String, Object> response);

}
