#!/bin/bash

# AS4SendController - Send Document Example (cURL)
#
# This script demonstrates how to send a UBL XML document via the AS4SendController.
#
# Requirements:
#   - curl command line tool
#   - Service running on http://localhost:3310
#   - UBL XML document (sample-invoice.xml)
#   - API token (default: "changeit", can be overridden)
#
# Usage:
#   # Using default token "changeit":
#   ./send-document-curl.sh
#
#   # With custom token via environment variable:
#   AS4_API_TOKEN="your-secret-token" ./send-document-curl.sh
#
#   # With custom token as first argument:
#   ./send-document-curl.sh "your-secret-token"

set -e

# Configuration
SERVICE_URL="http://localhost:3310"
SEND_ENDPOINT="/api/as4/send"

# API Token for Authorization
# Priority: command line argument > environment variable > default (changeit)
if [ -n "$1" ]; then
  API_TOKEN="$1"
else
  API_TOKEN="${AS4_API_TOKEN:-changeit}"
fi

# Identifiers (Sender, Receiver)
SENDER_ID="FI:OVT::003728468254"
RECEIVER_ID="FI:OVT::003728468254"

# Document Type Identifier (UBL Invoice)
DOC_TYPE_ID="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2::Invoice##DBNAlliance-1.0-data-Core"

# Process Identifier (no process)
PROCESS_ID="bdx:noprocess"

# XML document file
XML_FILE="sample-invoice.xml"

echo "=========================================="
echo "AS4SendController - Send Document Example"
echo "=========================================="
echo ""
echo "Configuration:"
echo "  Service URL:      $SERVICE_URL"
echo "  API Token:        $([ "$API_TOKEN" = "changeit" ] && echo "changeit (default)" || echo "$API_TOKEN")"
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

# URL-encode special characters in path variables
# In bash, we use printf with %q but for URLs we need proper encoding
# Convert # to %23 for URL encoding
DOC_TYPE_ID_ENCODED="${DOC_TYPE_ID//\#/%23}"

# Construct full URL
FULL_URL="$SERVICE_URL$SEND_ENDPOINT/$SENDER_ID/$RECEIVER_ID/$DOC_TYPE_ID_ENCODED/$PROCESS_ID"

echo "Sending request to: $FULL_URL"
echo ""
echo "Request Details:"
echo "  Method:       POST"
echo "  Content-Type: application/xml"
echo "  Authorization: Bearer $([ "$API_TOKEN" = "changeit" ] && echo "changeit (default)" || echo "$API_TOKEN")"
echo "  Body:         $(head -c 100 $XML_FILE)..."
echo ""

# Send the request
echo "Sending request..."
echo ""

curl -X POST \
  "$FULL_URL" \
  -H "Content-Type: application/xml" \
  -H "Authorization: Bearer $API_TOKEN" \
  -d @"$XML_FILE" \
  -w "\nHTTP Status: %{http_code}\n" \
  -v

echo ""
echo "=========================================="
echo "Request completed"
echo "=========================================="

