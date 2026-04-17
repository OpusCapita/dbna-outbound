package com.opuscapita.dbna.common.eventing;

import com.opuscapita.dbna.common.container.ContainerMessage;
import com.opuscapita.dbna.common.container.metadata.ContainerMessageMetadata;
import com.opuscapita.dbna.common.container.state.Route;
import com.opuscapita.dbna.common.container.state.log.DocumentLog;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.stream.Collectors;

/**
 * Creates informational message out of container message for failed documents.
 */
public class TicketContentFormatter {

    public static String getErrorDescription(ContainerMessage cm, Throwable e, String additionalDetails) {
        String detailedDescription = "Failed to process message";

        detailedDescription += "\nFile name: " + cm.getFileName();

        if (StringUtils.isNotBlank(cm.getCustomerId())) {
            detailedDescription += "\nCustomerID: " + cm.getCustomerId();
        }

        if (cm.getHistory().getLogs().size() > 0) {
            detailedDescription += "\nDocument history: " +
                    cm.getHistory().getLogs().stream().map(DocumentLog::toString).collect(Collectors.joining("\n\t ->"));
        }

        if (additionalDetails != null) {
            detailedDescription += "\nAdditional details: " + additionalDetails;
        }

        String json = containerMessageDumb(cm);
        detailedDescription += "\nMessage content dumb: \n" + json + "\n";

        String exceptionMessage = exceptionMessageToString(e);
        if (exceptionMessage != null) {
            detailedDescription += "\nPlatform exception message: " + exceptionMessage;
        }

        if (e != null) {
            detailedDescription += "\n\nPlatform exception trace: " + ExceptionUtils.getStackTrace(e) + "\n";
        }

        return detailedDescription;
    }

    public static String getErrorDescription(String customerId, String fileName, Throwable e, String additionalDetails) {
        String detailedDescription = "Failed to process message";

        if (fileName != null) {
            detailedDescription += "\nFile name: " + fileName;
        }

        if (StringUtils.isNotBlank(customerId)) {
            detailedDescription += "\nCustomerID: " + customerId;
        }

        if (additionalDetails != null) {
            detailedDescription += "\nAdditional details: " + additionalDetails;
        }

        String exceptionMessage = exceptionMessageToString(e);
        if (exceptionMessage != null) {
            detailedDescription += "\nPlatform exception message: " + exceptionMessage;
        }

        if (e != null) {
            detailedDescription += "\n\nPlatform exception trace: " + ExceptionUtils.getStackTrace(e) + "\n";
        }

        return detailedDescription;
    }

    public static String exceptionMessageToString(Throwable e) {
        if (e == null) {
            return null;
        }
        if (StringUtils.isBlank(e.getMessage())) {
            return null;
        }
        return e.getMessage();
    }

    public static String containerMessageDumb(ContainerMessage cm) {
        Route route = cm.getRoute();
        ContainerMessageMetadata metadata = cm.getMetadata();
        return String.format("ContainerMessage{filename: %s, source: %s, step: %s, transmissionId: %s, " +
                        "senderId: %s, receiverId: %s, sendingAccessPoint: %s, receivingAccessPoint: %s, " +
                        "documentTypeIdentifier: %s, profileTypeIdentifier: %s, protocol: %s, route: %s}",
                cm.getFileName(), cm.getSource(), cm.getStep(),
                metadata == null ? "null" : metadata.getTransmissionId(),
                metadata == null ? "null" : metadata.getSenderId(),
                metadata == null ? "null" : metadata.getRecipientId(),
                metadata == null ? "null" : metadata.getSendingAccessPoint(),
                metadata == null ? "null" : metadata.getReceivingAccessPoint(),
                metadata == null ? "null" : metadata.getDocumentTypeIdentifier(),
                metadata == null ? "null" : metadata.getProfileTypeIdentifier(),
                metadata == null ? "null" : metadata.getProtocol(),
                route == null ? "null" : route.toString());
    }
}

