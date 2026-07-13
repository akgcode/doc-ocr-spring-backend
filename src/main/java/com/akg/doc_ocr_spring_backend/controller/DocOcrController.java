package com.akg.doc_ocr_spring_backend.controller;

import com.akg.doc_ocr_spring_backend.config.OcrUploadProperties;
import com.akg.doc_ocr_spring_backend.dto.OcrResponse;
import com.akg.doc_ocr_spring_backend.entity.OcrJob;
import com.akg.doc_ocr_spring_backend.service.OcrService;
import com.akg.doc_ocr_spring_backend.logging.LoggerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/ocr/v0/pdf")
public class DocOcrController {

	private final OcrService ocrService;
	private final LoggerService logger;
	private final OcrUploadProperties uploadProperties;

	@Autowired
	public DocOcrController(OcrService ocrService, LoggerService logger, OcrUploadProperties uploadProperties) {
		this.ocrService = ocrService;
		this.logger = logger;
		this.uploadProperties = uploadProperties;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<OcrResponse> uploadPdf(@RequestParam("file") MultipartFile file) {
		if (file == null || file.isEmpty()) {
		    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			    .body(new OcrResponse(null, null, "no file provided"));
		}

		String contentType = file.getContentType();
		if (contentType == null || !uploadProperties.getAllowedContentTypes().contains(contentType.toLowerCase())) {
		    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
			    .body(new OcrResponse(file.getOriginalFilename(), null, "unsupported file type: allowed types are " + uploadProperties.getAllowedContentTypes()));
		}

		try {
		    OcrJob job = ocrService.processPdf(file);
			return ResponseEntity.ok(new OcrResponse(job.getId(), file.getOriginalFilename(), job.getExtractedText(), "success"));
		} catch (IOException e) {
		    logger.error("uploadPdf processing error for file={}", e, file.getOriginalFilename());
		    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			    .body(new OcrResponse(file.getOriginalFilename(), null, "processing error"));
		}
	}

}
