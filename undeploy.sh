#!/bin/bash
# Cleanup Kubernetes deployment

set -e

NAMESPACE="${NAMESPACE:-default}"

echo "Cleaning up DBNA Outbound from Kubernetes..."

# Delete deployment and related resources
kubectl delete -k ./k8s -n "$NAMESPACE" --ignore-not-found=true

echo "Cleanup complete!"

