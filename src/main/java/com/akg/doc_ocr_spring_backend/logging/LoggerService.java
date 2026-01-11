package com.akg.doc_ocr_spring_backend.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LoggerService {

    private final Logger logger = LoggerFactory.getLogger("app-logger");

    public void info(String message, Object... args) {
        logger.info(message, args);
    }

    public void warn(String message, Object... args) {
        logger.warn(message, args);
    }

    public void error(String message, Throwable t, Object... args) {
        if (t != null) {
            logger.error(String.format(message, args), t);
        } else {
            logger.error(message, args);
        }
    }

}
