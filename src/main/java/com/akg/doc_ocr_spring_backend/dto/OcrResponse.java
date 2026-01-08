package com.akg.doc_ocr_spring_backend.dto;

public class OcrResponse {

    private String filename;
    private String text;
    private String status;

    public OcrResponse() {
    }

    public OcrResponse(String filename, String text, String status) {
        this.filename = filename;
        this.text = text;
        this.status = status;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
