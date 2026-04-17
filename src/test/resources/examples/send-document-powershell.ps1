# AS4SendController - Send Document Example (PowerShell)
#
# This script demonstrates how to send a UBL XML document via the AS4SendController
# using Windows PowerShell.
#
# Requirements:
#   - PowerShell 5.0 or later
#   - Service running on http://localhost:8080
#   - UBL XML document (sample-invoice.xml)

# Configuration
$ServiceUrl = "http://localhost:8080"
$SendEndpoint = "/api/as4/send"

# Identifiers
$SenderId = "GLN::1234567890123"
$ReceiverId = "GLN::9876543210987"

# Document Type (UBL Invoice)
$DocTypeId = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"

# Process ID (PEPPOL BIS Billing)
$ProcessId = "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0"

# XML file path
$XmlFile = "sample-invoice.xml"

# Build full URL
$FullUrl = "$ServiceUrl$SendEndpoint/$SenderId/$ReceiverId/$DocTypeId/$ProcessId"

Write-Host "=========================================="
Write-Host "AS4SendController - Send Document Example"
Write-Host "=========================================="
Write-Host ""
Write-Host "Configuration:"
Write-Host "  Service URL:      $ServiceUrl"
Write-Host "  Sender ID:        $SenderId"
Write-Host "  Receiver ID:      $ReceiverId"
Write-Host "  Document Type:    $DocTypeId"
Write-Host "  Process ID:       $ProcessId"
Write-Host "  XML File:         $XmlFile"
Write-Host ""

# Check if XML file exists
if (-not (Test-Path $XmlFile)) {
    Write-Host "Error: XML file not found: $XmlFile"
    Write-Host "Please ensure sample-invoice.xml exists in the same directory."
    exit 1
}

# Read XML content
Write-Host "Reading XML file..."
$XmlContent = Get-Content -Path $XmlFile -Raw

Write-Host "Sending request to: $FullUrl"
Write-Host ""
Write-Host "Request Details:"
Write-Host "  Method:       POST"
Write-Host "  Content-Type: application/xml"
Write-Host "  Body Size:    $($XmlContent.Length) bytes"
Write-Host ""

try {
    Write-Host "Sending request..."
    Write-Host ""

    $Response = Invoke-WebRequest `
        -Uri $FullUrl `
        -Method POST `
        -Headers @{ "Content-Type" = "application/xml" } `
        -Body $XmlContent `
        -ContentType "application/xml"

    Write-Host "Response Status: $($Response.StatusCode) $([System.Net.HttpStatusCode]$Response.StatusCode)"
    Write-Host ""
    Write-Host "Response Body:"
    Write-Host $Response.Content
    Write-Host ""

    if ($Response.StatusCode -eq 200) {
        Write-Host "✓ Request successful"

        # Parse JSON response
        $JsonResponse = $Response.Content | ConvertFrom-Json
        Write-Host ""
        Write-Host "Message Details:"
        Write-Host "  Success:    $($JsonResponse.success)"
        Write-Host "  Message ID: $($JsonResponse.messageId)"
        Write-Host "  Status:     $($JsonResponse.status)"
        Write-Host "  Timestamp:  $($JsonResponse.timestamp)"
    }
    else {
        Write-Host "✗ Request failed with status $($Response.StatusCode)"
    }
}
catch [System.Net.WebException] {
    Write-Host "✗ Request failed"
    Write-Host "Error: $_"

    if ($_.Exception.Response) {
        $StatusCode = $_.Exception.Response.StatusCode.Value__
        $ResponseBody = $_.Exception.Response.GetResponseStream()
        $Reader = New-Object System.IO.StreamReader($ResponseBody)
        $ResponseText = $Reader.ReadToEnd()

        Write-Host "HTTP Status: $StatusCode"
        Write-Host "Response:"
        Write-Host $ResponseText
    }
    exit 1
}

Write-Host ""
Write-Host "=========================================="
Write-Host "Request completed"
Write-Host "=========================================="

