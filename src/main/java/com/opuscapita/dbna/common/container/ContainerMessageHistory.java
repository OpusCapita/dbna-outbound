package com.opuscapita.dbna.common.container;

import com.opuscapita.dbna.common.container.state.ProcessStep;
import com.opuscapita.dbna.common.container.state.log.DocumentErrorType;
import com.opuscapita.dbna.common.container.state.log.DocumentLog;
import com.opuscapita.dbna.common.container.state.log.DocumentLogLevel;
import com.opuscapita.dbna.common.container.state.log.DocumentValidationError;
import com.google.gson.annotations.Since;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tracks the current processing state of a container message.
 */
public class ContainerMessageHistory implements Serializable {

    private static final long serialVersionUID = -9016219790420895460L;

    @Since(1.0)
    private ProcessStep step;
    @Since(1.0)
    private List<DocumentLog> logs;

    ContainerMessageHistory(ProcessStep step) {
        this.step = step;
        this.logs = new ArrayList<>();
    }

    public ProcessStep getStep() {
        return step;
    }

    public void setStep(ProcessStep step) {
        this.step = step;
    }

    public List<DocumentLog> getLogs() {
        return logs;
    }

    public void setLogs(List<DocumentLog> logs) {
        this.logs = logs;
    }

    public void addLog(DocumentLog log) {
        log.setSource(step);
        this.logs.add(log);
    }

    public void addInfo(String message) {
        this.addLog(new DocumentLog(message, DocumentLogLevel.INFO));
    }

    public void addWarning(String message) {
        this.addLog(new DocumentLog(message, DocumentLogLevel.WARNING));
    }

    public void addError(String message) {
        this.addLog(new DocumentLog(message, DocumentLogLevel.ERROR, DocumentErrorType.PROCESSING_ERROR));
    }

    public void addSendingError(String message) {
        this.addLog(new DocumentLog(message, DocumentLogLevel.ERROR, DocumentErrorType.SENDING_ERROR));
    }

    public void addValidationError(String message) {
        this.addLog(new DocumentLog(message, DocumentLogLevel.ERROR, DocumentErrorType.VALIDATION_ERROR));
    }

    public void addValidationError(DocumentValidationError error) {
        DocumentLog log = new DocumentLog(error.toString(), DocumentLogLevel.ERROR, DocumentErrorType.VALIDATION_ERROR);
        log.setValidationError(error);
        this.addLog(log);
    }

    public void addValidationWarning(DocumentValidationError error) {
        DocumentLog log = new DocumentLog(error.toString(), DocumentLogLevel.WARNING, DocumentErrorType.VALIDATION_ERROR);
        log.setValidationError(error);
        this.addLog(log);
    }

    public boolean hasError() {
        return this.logs.stream().anyMatch(DocumentLog::isError);
    }

    public List<DocumentLog> getErrors() {
        return this.logs.stream().filter(DocumentLog::isError).collect(Collectors.toList());
    }

    public List<DocumentLog> getWarnings() {
        return this.logs.stream().filter(DocumentLog::isWarning).collect(Collectors.toList());
    }

    public List<DocumentLog> getValidationErrors() {
        return this.logs.stream().filter(DocumentLog::isValidationError).collect(Collectors.toList());
    }

    public List<DocumentLog> getValidationWarnings() {
        return this.logs.stream().filter(log -> log.isWarning() && DocumentErrorType.VALIDATION_ERROR.equals(log.getErrorType())).collect(Collectors.toList());
    }

    public DocumentLog getLastLog() {
        if (logs.isEmpty()) {
            return null;
        }
        return logs.get(logs.size() - 1);
    }

    public DocumentLog getLastLog(DocumentLogLevel level) {
        if (logs.isEmpty()) {
            return null;
        }

        for (int i = logs.size() - 1; i >= 0; i--) {
            DocumentLog log = logs.get(i);
            if (level.equals(log.getLevel())) {
                return log;
            }
        }
        return null;
    }

    public DocumentLog getLastInfoLog() {
        return getLastLog(DocumentLogLevel.INFO);
    }

    public DocumentLog getLastErrorLog() {
        return getLastLog(DocumentLogLevel.ERROR);
    }
}
