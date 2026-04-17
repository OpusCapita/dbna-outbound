#!/bin/bash
# Kubernetes local deployment using Docker for Mac/Linux

set -e

# Configuration
IMAGE_NAME="dbna-outbound"
IMAGE_TAG="latest"
NAMESPACE="dbna"

echo "Setting up local Kubernetes environment for DBNA Outbound..."

# Build application
echo "Building DBNA Outbound..."
./gradlew bootJar --no-daemon

# Build Docker image
echo "Building Docker image..."
docker build -t "${IMAGE_NAME}:${IMAGE_TAG}" .

# Create namespace
echo "Creating Kubernetes namespace: $NAMESPACE"
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

# Load image into Kubernetes (for Docker Desktop/Minikube)
echo "Loading Docker image into Kubernetes..."
if command -v minikube &> /dev/null; then
    minikube image load "${IMAGE_NAME}:${IMAGE_TAG}"
fi

# Deploy with Kustomize
echo "Deploying to Kubernetes..."
kubectl apply -k ./k8s -n "$NAMESPACE"

# Wait for deployment
echo "Waiting for deployment to be ready..."
kubectl rollout status deployment/dbna-outbound -n "$NAMESPACE" --timeout=300s

# Show status
echo "Deployment Status:"
kubectl get all -n "$NAMESPACE" -l app=dbna-outbound

# Port forwarding information
echo ""
echo "To access the application locally, run:"
echo "kubectl port-forward -n $NAMESPACE svc/dbna-outbound 8080:80"
echo ""
echo "Then access the application at: http://localhost:8080"
echo "Health check: http://localhost:8080/actuator/health"

