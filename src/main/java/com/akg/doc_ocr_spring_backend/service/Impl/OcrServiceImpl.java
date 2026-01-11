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

        logger.info("Parsing OCR response. Keys: {}", response.keySet());

        Object isErrored = response.get("IsErroredOnProcessing");
        if (isErrored != null && Boolean.TRUE.equals(isErrored)) {
            Object errorMessage = response.get("ErrorMessage");
            String error = "OCR Error: " + (errorMessage != null ? errorMessage.toString() : "Unknown error");
            parsedTexts.add(error);
            return parsedTexts;
        }

        // Extract ParsedResults array
        Object parsedResultsObj = response.get("ParsedResults");
        if (parsedResultsObj instanceof List) {
            List<Map<String, Object>> parsedResults = (List<Map<String, Object>>) parsedResultsObj;
            for (int i = 0; i < parsedResults.size(); i++) {
                Map<String, Object> result = parsedResults.get(i);

                OcrSpaceResponse dto = new OcrSpaceResponse();
                Object exitCode = result.get("FileParseExitCode");
                if (exitCode instanceof Integer) {
                    dto.setOcrExitCode((Integer) exitCode);
                }

                Object parsedText = result.get("ParsedText");
                if (parsedText != null) {
                    dto.setParsedText(parsedText.toString());
                }

                Object errorMessage = result.get("ErrorMessage");
                if (errorMessage != null && !errorMessage.toString().isEmpty()) {
                    dto.setErrorMessage(errorMessage.toString());
                }

                Object errorDetails = result.get("ErrorDetails");
                if (errorDetails != null && !errorDetails.toString().isEmpty()) {
                    dto.setErrorDetails(errorDetails.toString());
                }

                if (dto.getParsedText() != null && !dto.getParsedText().isEmpty()) {
                    parsedTexts.add(dto.getParsedText());
                } else if (dto.getErrorMessage() != null) {
                    String error = "Page " + (i + 1) + " Error: " + dto.getErrorMessage();
                    parsedTexts.add(error);
                }
            }
        } else {
            logger.warn("ParsedResults not found or not a list in response");
        }

        if (parsedTexts.isEmpty()) {
            parsedTexts.add("Error: No parsed text extracted from OCR response");
        }
        return parsedTexts;
    }

}