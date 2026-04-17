#!/bin/bash

# AS4SendController - Send Document Example (cURL)
#
# This script demonstrates how to send a UBL XML document via the AS4SendController.
#
# Requirements:
#   - curl command line tool
#   - Service running on http://localhost:8080
#   - UBL XML document (sample-invoice.xml)

set -e

# Configuration
SERVICE_URL="http://localhost:8080"
SEND_ENDPOINT="/api/as4/send"

# Identifiers (Sender, Receiver)
SENDER_ID="GLN::1234567890123"
RECEIVER_ID="DUNS::079961550"

# Document Type Identifier (UBL Invoice)
DOC_TYPE_ID="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"

# Process Identifier (PEPPOL BIS Billing)
PROCESS_ID="urn:fdc:peppol.eu:2017:poacc:billing:01:1.0"

# XML document file
XML_FILE="sample-invoice.xml"

echo "=========================================="
echo "AS4SendController - Send Document Example"
echo "=========================================="
echo ""
echo "Configuration:"
echo "  Service URL:      $SERVICE_URL"
echo "  Sender ID:        $SENDER_ID"
echo "  Receiver ID:      $RECEIVER_ID"
echo "  Document Type:    $DOC_TYPE_ID"
echo "  Process ID:       $PROCESS_ID"
echo "  XML File:         $XML_FILE"
echo ""

# Check if XML file exists
if [ ! -f "$XML_FILE" ]; then
    echo "Error: XML file not found: $XML_FILE"
    echo "Please ensure sample-invoice.xml exists in the same directory."
    exit 1
fi

# Construct full URL
FULL_URL="$SERVICE_URL$SEND_ENDPOINT/$SENDER_ID/$RECEIVER_ID/$DOC_TYPE_ID/$PROCESS_ID"

echo "Sending request to: $FULL_URL"
echo ""
echo "Request Details:"
echo "  Method:       POST"
echo "  Content-Type: application/xml"
echo "  Body:         $(head -c 100 $XML_FILE)..."
echo ""

# Send the request
echo "Sending request..."
echo ""

curl -X POST \
  "$FULL_URL" \
  -H "Content-Type: application/xml" \
  -d @"$XML_FILE" \
  -w "\nHTTP Status: %{http_code}\n" \
  -s

echo ""
echo "=========================================="
echo "Request completed"
echo "=========================================="

