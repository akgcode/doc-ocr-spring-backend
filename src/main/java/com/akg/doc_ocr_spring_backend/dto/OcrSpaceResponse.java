package com.akg.doc_ocr_spring_backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OcrSpaceResponse {

    private Integer ocrExitCode;
    private String parsedText;
    private Boolean isErroredOnProcessing;
    private String errorMessage;
    private String errorDetails;

    public OcrSpaceResponse() {
    }

    public OcrSpaceResponse(Integer ocrExitCode, String parsedText, Boolean isErroredOnProcessing, String errorMessage) {
        this.ocrExitCode = ocrExitCode;
        this.parsedText = parsedText;
        this.isErroredOnProcessing = isErroredOnProcessing;
        this.errorMessage = errorMessage;
    }

    public Integer getOcrExitCode() {
        return ocrExitCode;
    }

    public void setOcrExitCode(Integer ocrExitCode) {
        this.ocrExitCode = ocrExitCode;
    }

    public String getParsedText() {
        return parsedText;
    }

    public void setParsedText(String parsedText) {
        this.parsedText = parsedText;
    }

    public Boolean getIsErroredOnProcessing() {
        return isErroredOnProcessing;
    }

    public void setIsErroredOnProcessing(Boolean erroredOnProcessing) {
        isErroredOnProcessing = erroredOnProcessing;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorDetails() {
        return errorDetails;
    }

    public void setErrorDetails(String errorDetails) {
        this.errorDetails = errorDetails;
    }
}
