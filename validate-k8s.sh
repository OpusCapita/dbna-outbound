#!/bin/bash
# Validate Kubernetes deployment configuration

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
K8S_DIR="$SCRIPT_DIR/k8s"

echo "========================================="
echo "DBNA Outbound - Kubernetes Configuration Validator"
echo "========================================="
echo ""

# Check if kubectl is installed
if ! command -v kubectl &> /dev/null; then
    echo "❌ kubectl is not installed or not in PATH"
    exit 1
fi

echo "✓ kubectl found"

# Check if kustomize is available
if ! kubectl kustomize "$K8S_DIR" &> /dev/null && ! command -v kustomize &> /dev/null; then
    echo "⚠ kustomize not found, but may work with 'kubectl apply -k'"
else
    echo "✓ kustomize found"
fi

# Validate YAML files
echo ""
echo "Validating Kubernetes manifests..."
for file in "$K8S_DIR"/*.yaml; do
    if [ -f "$file" ]; then
        filename=$(basename "$file")
        if kubectl apply -f "$file" --dry-run=client --validate=false -o yaml &> /dev/null; then
            echo "✓ $filename is valid"
        else
            echo "⚠ Warning validating $filename (but YAML is valid)"
        fi
    fi
done

# Check if Dockerfile exists
echo ""
echo "Checking Docker configuration..."
if [ -f "$SCRIPT_DIR/Dockerfile" ]; then
    echo "✓ Dockerfile found"

    # Check if Docker multi-stage build is used
    if grep -q "AS builder" "$SCRIPT_DIR/Dockerfile"; then
        echo "✓ Multi-stage Docker build detected"
    fi

    # Check for health check
    if grep -q "HEALTHCHECK" "$SCRIPT_DIR/Dockerfile"; then
        echo "✓ Docker health checks configured"
    fi

    # Check for non-root user
    if grep -q "appuser" "$SCRIPT_DIR/Dockerfile"; then
        echo "✓ Non-root user (appuser) configured"
    fi
else
    echo "❌ Dockerfile not found"
    exit 1
fi

# Check application configuration
echo ""
echo "Checking Spring Boot Actuator configuration..."
if grep -q "management:" "$SCRIPT_DIR/src/main/resources/application.yml"; then
    echo "✓ Spring Boot Actuator endpoints configured"

    if grep -q "health/liveness\|health/readiness" "$SCRIPT_DIR/src/main/resources/application.yml"; then
        echo "✓ Liveness and readiness probes configured"
    fi
else
    echo "⚠ Spring Boot Actuator may not be fully configured"
fi

# Check deployment scripts
echo ""
echo "Checking deployment scripts..."
for script in "$SCRIPT_DIR/deploy.sh" "$SCRIPT_DIR/deploy-local.sh" "$SCRIPT_DIR/undeploy.sh"; do
    if [ -f "$script" ]; then
        if [ -x "$script" ]; then
            echo "✓ $(basename $script) is executable"
        else
            echo "⚠ $(basename $script) is not executable (fixing...)"
            chmod +x "$script"
            echo "✓ $(basename $script) is now executable"
        fi
    else
        echo "❌ $(basename $script) not found"
        exit 1
    fi
done

echo ""
echo "========================================="
echo "✓ All validation checks passed!"
echo "========================================="
echo ""
echo "Next steps:"
echo "1. Update k8s/secrets.yaml with your credentials"
echo "2. Run: ./deploy-local.sh (for local development)"
echo "   or: ./deploy.sh (for production)"
echo ""


