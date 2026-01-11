package com.akg.doc_ocr_spring_backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OcrSpaceResponse {

    @JsonProperty("OCRExitCode")
    private Integer ocrExitCode;

    @JsonProperty("ParsedText")
    private String parsedText;

    @JsonProperty("IsErroredOnProcessing")
    private Boolean isErroredOnProcessing;

    @JsonProperty("ErrorMessage")
    private String errorMessage;

    @JsonProperty("ErrorDetails")
    private String errorDetails;

    @JsonProperty("ParsedResults")
    private List<ParsedResult> parsedResults;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ParsedResult {
        @JsonProperty("ParsedText")
        private String parsedText;

        @JsonProperty("FileParseExitCode")
        private Integer fileParseExitCode;

        @JsonProperty("ErrorMessage")
        private String errorMessage;

        @JsonProperty("ErrorDetails")
        private String errorDetails;

        public String getParsedText() {
            return parsedText;
        }

        public void setParsedText(String parsedText) {
            this.parsedText = parsedText;
        }

        public Integer getFileParseExitCode() {
            return fileParseExitCode;
        }

        public void setFileParseExitCode(Integer fileParseExitCode) {
            this.fileParseExitCode = fileParseExitCode;
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

    public List<ParsedResult> getParsedResults() {
        return parsedResults;
    }

    public void setParsedResults(List<ParsedResult> parsedResults) {
        this.parsedResults = parsedResults;
    }
}
