package com.akg.doc_ocr_spring_backend.service.Impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.akg.doc_ocr_spring_backend.dto.OcrSpaceResponse;
import com.akg.doc_ocr_spring_backend.logging.LoggerService;
import com.akg.doc_ocr_spring_backend.service.OcrService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class OcrServiceImpl implements OcrService {

    @Value("${ocr.space.api.url}")
    private String ocrSpaceApiUrl;

    @Value("${ocr.space.api.key}")
    private String ocrSpaceApiKey;

    private final RestTemplate restTemplate;
    private final LoggerService logger;

    public OcrServiceImpl(RestTemplate restTemplate, LoggerService logger) {
        this.restTemplate = restTemplate;
        this.logger = logger;
    }

    @Override
    public String processPdf(MultipartFile file) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("apikey", ocrSpaceApiKey);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file.getResource());

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            logger.info("Calling OCR Space API url={} filename={}", ocrSpaceApiUrl, file.getOriginalFilename());

            ResponseEntity<Map> responseEntity = restTemplate.exchange(
                    ocrSpaceApiUrl,
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );
            Map<String, Object> response = responseEntity.getBody();

            List<String> parsedTexts = parsePdf(response);
            String result = String.join("\n---\n", parsedTexts);
            logger.info("OCR Space API returned for file={} extracted {} pages", file.getOriginalFilename(), parsedTexts.size());
            return result;
        } catch (Exception e) {
            logger.error("Failed to process PDF with OCR Space API for file={}", e, file.getOriginalFilename());
            throw new IOException("Failed to process PDF with OCR Space API", e);
        }
    }

    public List<String> parsePdf(Map<String, Object> response) {
        List<String> parsedTexts = new ArrayList<>();

        if (response == null) {
            logger.warn("Response is null, returning empty list");
            return parsedTexts;
        }

        logger.info("Parsing OCR response with ObjectMapper");

        try {
            // Convert Map to OcrSpaceResponse DTO using ObjectMapper
            ObjectMapper objectMapper = new ObjectMapper();
            OcrSpaceResponse ocrResponse = objectMapper.convertValue(response, OcrSpaceResponse.class);

            logger.info("Successfully converted response to OcrSpaceResponse DTO");

            // Check for top-level errors
            if (Boolean.TRUE.equals(ocrResponse.getIsErroredOnProcessing())) {
                String errorMsg = ocrResponse.getErrorMessage() != null ? ocrResponse.getErrorMessage() : "Unknown error";
                logger.warn("OCR processing returned error: {}", errorMsg);
                parsedTexts.add("OCR Error: " + errorMsg);
                return parsedTexts;
            }

            // Extract parsed texts from ParsedResults array
            if (ocrResponse.getParsedResults() != null && !ocrResponse.getParsedResults().isEmpty()) {
                logger.info("Found {} parsed results in response", ocrResponse.getParsedResults().size());

                for (int i = 0; i < ocrResponse.getParsedResults().size(); i++) {
                    OcrSpaceResponse.ParsedResult result = ocrResponse.getParsedResults().get(i);
                    logger.info("Processing ParsedResult index: {}", i);

                    if (result.getParsedText() != null && !result.getParsedText().isEmpty()) {
                        logger.info("Extracted ParsedText from result index {}, length: {}", i, result.getParsedText().length());
                        parsedTexts.add(result.getParsedText());
                    } else if (result.getErrorMessage() != null && !result.getErrorMessage().isEmpty()) {
                        String error = "Page " + (i + 1) + " Error: " + result.getErrorMessage();
                        logger.warn("{}", error);
                        parsedTexts.add(error);
                    }
                }
            } else {
                logger.warn("No ParsedResults found in response");
            }

            if (parsedTexts.isEmpty()) {
                logger.warn("No parsed texts extracted from response");
                parsedTexts.add("Error: No parsed text extracted from OCR response");
            }

            logger.info("Parsing complete. Total pages extracted: {}", parsedTexts.size());
            return parsedTexts;

        } catch (Exception e) {
            logger.error("Error converting response to OcrSpaceResponse DTO", e);
            parsedTexts.add("Error: Failed to parse OCR response");
            return parsedTexts;
        }
    }

}