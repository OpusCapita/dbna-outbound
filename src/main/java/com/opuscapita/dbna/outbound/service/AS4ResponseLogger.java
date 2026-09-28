package com.opuscapita.dbna.outbound.service;

import com.helger.phase4.client.AS4ClientSentMessage;
import com.helger.phase4.client.IAS4RawResponseConsumer;
import com.helger.phase4.client.IAS4SignalMessageConsumer;
import com.helger.phase4.ebms3header.Ebms3Error;
import com.helger.phase4.ebms3header.Ebms3MessageInfo;
import com.helger.phase4.ebms3header.Ebms3SignalMessage;
import com.helger.phase4.messaging.IAS4IncomingMessageMetadata;
import com.helger.phase4.servlet.IAS4MessageState;
import com.opuscapita.dbna.outbound.util.XmlUtil;
import jakarta.mail.BodyPart;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import org.apache.hc.core5.http.message.StatusLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

/**
 * Logs everything Phase4 exposes about the response of an AS4 transmission:
 * <ul>
 *   <li>DEBUG - HTTP status, headers, parsed ebMS signal message (receipt / errors), processing state
 *       (signature / decryption results, certificates, WSS4J exception, PMode, profile)</li>
 *   <li>TRACE - raw HTTP response body (pretty-printed XML, multipart split into parts) and the
 *       original / decrypted SOAP documents</li>
 * </ul>
 * The raw response consumer runs before any WSS4J processing, so the raw response is logged even if
 * signature verification or decryption of the response fails.
 */
public final class AS4ResponseLogger {
    private static final Logger logger = LoggerFactory.getLogger(AS4ResponseLogger.class);

    private AS4ResponseLogger() {
    }

    public static IAS4RawResponseConsumer rawResponseConsumer() {
        return AS4ResponseLogger::logRawResponse;
    }

    public static IAS4SignalMessageConsumer signalMessageConsumer() {
        return AS4ResponseLogger::logSignalMessage;
    }

    // ---------------------------------------------------------------------------------------------
    // Raw HTTP response
    // ---------------------------------------------------------------------------------------------

    private static void logRawResponse(AS4ClientSentMessage<byte[]> response) {
        try {
            if (logger.isDebugEnabled()) {
                StringBuilder sb = new StringBuilder("\n======== AS4 RAW RESPONSE (metadata) ========\n");
                sb.append("  Request Message ID: ").append(response.getMessageID()).append('\n');
                sb.append("  Sent at:            ").append(response.getSentDateTime()).append('\n');
                if (response.hasResponseStatusLine()) {
                    StatusLine status = response.getResponseStatusLine();
                    sb.append("  HTTP status:        ").append(status.getProtocolVersion()).append(' ')
                        .append(status.getStatusCode()).append(' ').append(status.getReasonPhrase()).append('\n');
                } else {
                    sb.append("  HTTP status:        <not available>\n");
                }
                sb.append("  Response size:      ")
                    .append(response.hasResponse() ? response.getResponse().length + " bytes" : "<empty>").append('\n');
                sb.append("  HTTP headers:\n");
                if (response.getResponseHeaders() != null) {
                    response.getResponseHeaders().getAllHeaderLines(true)
                        .forEach(line -> sb.append("    ").append(line).append('\n'));
                }
                sb.append("=============================================");
                logger.debug(sb.toString());
            }

            if (logger.isTraceEnabled() && response.hasResponse()) {
                String contentType = response.getResponseHeaders() != null
                    ? response.getResponseHeaders().getFirstHeaderValue("Content-Type") : null;
                logger.trace("\n======== AS4 RAW RESPONSE (body) ========\n{}\n=========================================",
                    formatBody(response.getResponse(), contentType));
            }
        } catch (Exception e) {
            logger.warn("Failed to log raw AS4 response: {}", e.getMessage(), e);
        }
    }

    private static String formatBody(byte[] body, String contentType) {
        if (contentType != null && contentType.toLowerCase().contains("multipart")) {
            try {
                MimeMultipart multipart = new MimeMultipart(new ByteArrayDataSource(body, contentType));
                StringBuilder sb = new StringBuilder();
                sb.append("Multipart message with ").append(multipart.getCount()).append(" part(s)\n");
                for (int i = 0; i < multipart.getCount(); i++) {
                    BodyPart part = multipart.getBodyPart(i);
                    sb.append("\n--- Part #").append(i + 1).append(" ---\n");
                    Enumeration<jakarta.mail.Header> headers = part.getAllHeaders();
                    while (headers.hasMoreElements()) {
                        jakarta.mail.Header h = headers.nextElement();
                        sb.append(h.getName()).append(": ").append(h.getValue()).append('\n');
                    }
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    part.getInputStream().transferTo(out);
                    sb.append('\n').append(formatSinglePart(out.toByteArray(), part.getContentType())).append('\n');
                }
                return sb.toString();
            } catch (Exception e) {
                logger.debug("Could not parse multipart response, logging as text: {}", e.getMessage());
            }
        }
        return formatSinglePart(body, contentType);
    }

    private static String formatSinglePart(byte[] data, String contentType) {
        String ct = contentType == null ? "" : contentType.toLowerCase();
        String text = new String(data, StandardCharsets.UTF_8).trim();
        if (ct.contains("xml") || text.startsWith("<")) {
            return XmlUtil.prettyPrintXml(data);
        }
        if (ct.startsWith("text/") || ct.contains("json")) {
            return text;
        }
        return "<binary content, " + data.length + " bytes, Content-Type: " + contentType + ">";
    }

    // ---------------------------------------------------------------------------------------------
    // Parsed signal message
    // ---------------------------------------------------------------------------------------------

    private static void logSignalMessage(Ebms3SignalMessage signal,
                                         IAS4IncomingMessageMetadata metadata,
                                         IAS4MessageState state) {
        try {
            if (logger.isDebugEnabled()) {
                StringBuilder sb = new StringBuilder("\n======== AS4 SIGNAL MESSAGE ========\n");
                appendSignal(sb, signal);
                appendMetadata(sb, metadata);
                appendState(sb, state);
                sb.append("====================================");
                logger.debug(sb.toString());
            }

            if (logger.isTraceEnabled()) {
                logger.trace("\n======== AS4 RESPONSE SOAP (original) ========\n{}\n==============================================",
                    toPrettyXml(state.getOriginalSoapDocument()));
                if (state.getDecryptedSoapDocument() != null) {
                    logger.trace("\n======== AS4 RESPONSE SOAP (decrypted) ========\n{}\n===============================================",
                        toPrettyXml(state.getDecryptedSoapDocument()));
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to log AS4 signal message: {}", e.getMessage(), e);
        }
    }

    private static void appendSignal(StringBuilder sb, Ebms3SignalMessage signal) {
        Ebms3MessageInfo info = signal.getMessageInfo();
        sb.append("Signal:\n");
        if (info != null) {
            sb.append("  Message ID:          ").append(info.getMessageId()).append('\n');
            sb.append("  RefToMessageId:      ").append(info.getRefToMessageId()).append('\n');
            sb.append("  Timestamp:           ").append(info.getTimestamp()).append('\n');
        }
        sb.append("  Receipt present:     ").append(signal.getReceipt() != null).append('\n');
        if (signal.getReceipt() != null) {
            sb.append("  Receipt elements:    ").append(signal.getReceipt().getAnyCount()).append('\n');
        }
        sb.append("  PullRequest present: ").append(signal.getPullRequest() != null).append('\n');
        sb.append("  Errors:              ").append(signal.getErrorCount()).append('\n');
        for (Ebms3Error error : signal.getError()) {
            sb.append("    - code=").append(error.getErrorCode())
                .append(", severity=").append(error.getSeverity())
                .append(", category=").append(error.getCategory())
                .append(", shortDescription=").append(error.getShortDescription()).append('\n');
            sb.append("      description=").append(error.getDescription() != null ? error.getDescription().getValue() : null).append('\n');
            sb.append("      errorDetail=").append(error.getErrorDetail()).append('\n');
            sb.append("      origin=").append(error.getOrigin())
                .append(", refToMessageInError=").append(error.getRefToMessageInError()).append('\n');
        }
    }

    private static void appendMetadata(StringBuilder sb, IAS4IncomingMessageMetadata metadata) {
        if (metadata == null) {
            return;
        }
        sb.append("Incoming metadata:\n");
        sb.append("  Incoming unique ID:  ").append(metadata.getIncomingUniqueID()).append('\n');
        sb.append("  Incoming at:         ").append(metadata.getIncomingDT()).append('\n');
        sb.append("  Mode:                ").append(metadata.getMode()).append('\n');
        sb.append("  Remote address:      ").append(metadata.getRemoteAddr()).append('\n');
        sb.append("  Request message ID:  ").append(metadata.getRequestMessageID()).append('\n');
    }

    private static void appendState(StringBuilder sb, IAS4MessageState state) {
        if (state == null) {
            return;
        }
        sb.append("Processing state:\n");
        sb.append("  Message ID:                  ").append(state.getMessageID()).append('\n');
        sb.append("  RefToMessageID:              ").append(state.getRefToMessageID()).append('\n');
        sb.append("  Message timestamp:           ").append(state.getMessageTimestamp()).append('\n');
        sb.append("  Receipt DT:                  ").append(state.getReceiptDT()).append('\n');
        sb.append("  SOAP version:                ").append(state.getSoapVersion()).append('\n');
        sb.append("  PMode:                       ").append(state.getPMode() != null ? state.getPMode().getID() : null).append('\n');
        sb.append("  Effective PMode leg:         ").append(state.getEffectivePModeLegNumber()).append('\n');
        sb.append("  AS4 profile:                 ").append(state.getAS4Profile() != null ? state.getAS4Profile().getID() : null).append('\n');
        sb.append("  Initiator / Responder:       ").append(state.getInitiatorID()).append(" / ").append(state.getResponderID()).append('\n');
        sb.append("  WSS4J security actions:      ").append(state.getSoapWSS4JSecurityActions()).append('\n');
        sb.append("  Signature checked:           ").append(state.isSoapSignatureChecked()).append('\n');
        sb.append("  Decrypted:                   ").append(state.isSoapDecrypted()).append('\n');
        sb.append("  Header processing successful:").append(' ').append(state.isSoapHeaderElementProcessingSuccessful()).append('\n');
        sb.append("  Ping message:                ").append(state.isPingMessage()).append('\n');
        appendCertificate(sb, "Signing certificate", state.getSigningCertificate());
        appendCertificate(sb, "Used certificate", state.getUsedCertificate());
        appendCertificate(sb, "Decrypting certificate", state.getDecryptingCertificate());
        if (state.hasSoapWSS4JException()) {
            Exception ex = state.getSoapWSS4JException();
            sb.append("  WSS4J exception:             ").append(ex.getClass().getName()).append(": ").append(ex.getMessage()).append('\n');
            Throwable cause = ex.getCause();
            while (cause != null) {
                sb.append("    caused by: ").append(cause.getClass().getName()).append(": ").append(cause.getMessage()).append('\n');
                cause = cause.getCause();
            }
        }
    }

    private static void appendCertificate(StringBuilder sb, String label, X509Certificate cert) {
        if (cert == null) {
            sb.append("  ").append(label).append(": <none>\n");
            return;
        }
        sb.append("  ").append(label).append(":\n");
        sb.append("    Subject: ").append(cert.getSubjectX500Principal()).append('\n');
        sb.append("    Issuer:  ").append(cert.getIssuerX500Principal()).append('\n');
        sb.append("    Serial:  ").append(cert.getSerialNumber().toString(16)).append('\n');
        sb.append("    Valid:   ").append(cert.getNotBefore()).append(" - ").append(cert.getNotAfter()).append('\n');
    }

    private static String toPrettyXml(Document doc) {
        if (doc == null) {
            return "<none>";
        }
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            StringWriter sw = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(sw));
            return sw.toString().replaceAll("(?m)^[ \\t]*\\r?\\n", "");
        } catch (Exception e) {
            return "<failed to serialize document: " + e.getMessage() + ">";
        }
    }
}

