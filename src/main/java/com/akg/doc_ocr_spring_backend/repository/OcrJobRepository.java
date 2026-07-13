package com.akg.doc_ocr_spring_backend.repository;

import com.akg.doc_ocr_spring_backend.entity.OcrJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OcrJobRepository extends JpaRepository<OcrJob, Long> {
}
