#!/bin/bash
# Build and deploy DBNA Outbound to Kubernetes

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
IMAGE_NAME="${IMAGE_NAME:-dbna-outbound}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
REGISTRY="${REGISTRY:-}"
NAMESPACE="${NAMESPACE:-default}"
KUSTOMIZE_DIR="./k8s"

echo -e "${YELLOW}Building DBNA Outbound application...${NC}"

# Build the application
./gradlew bootJar --no-daemon

echo -e "${YELLOW}Building Docker image...${NC}"

# Build Docker image
FULL_IMAGE_NAME="${REGISTRY}${IMAGE_NAME}:${IMAGE_TAG}"

if [ -z "$REGISTRY" ]; then
    docker build -t "${IMAGE_NAME}:${IMAGE_TAG}" .
else
    docker build -t "${FULL_IMAGE_NAME}" .
    docker push "${FULL_IMAGE_NAME}"
fi

echo -e "${GREEN}Docker image built successfully: ${FULL_IMAGE_NAME}${NC}"

# Verify Kubernetes cluster connection
echo -e "${YELLOW}Checking Kubernetes cluster connection...${NC}"
kubectl cluster-info

# Create namespace if it doesn't exist
if [ "$NAMESPACE" != "default" ]; then
    kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -
    echo -e "${GREEN}Namespace '$NAMESPACE' ready${NC}"
fi

# Deploy using Kustomize
echo -e "${YELLOW}Deploying to Kubernetes namespace: $NAMESPACE${NC}"
kubectl apply -k "$KUSTOMIZE_DIR" -n "$NAMESPACE"

echo -e "${YELLOW}Waiting for deployment to be ready...${NC}"
kubectl rollout status deployment/dbna-outbound -n "$NAMESPACE" --timeout=300s

echo -e "${GREEN}Deployment successful!${NC}"

# Show deployment info
echo -e "${YELLOW}Deployment Information:${NC}"
kubectl get deployment,pods,svc -n "$NAMESPACE" -l app=dbna-outbound

echo -e "${YELLOW}Pod logs:${NC}"
kubectl logs -n "$NAMESPACE" -l app=dbna-outbound --tail=20

echo -e "${GREEN}DBNA Outbound is now deployed and running!${NC}"

