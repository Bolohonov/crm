#!/bin/bash
set -euo pipefail

# ── Config ──
REGISTRY="localhost:5000"
NAMESPACE="crm"
RELEASE="crm-app"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "[deploy] Starting CRM deployment..."

# ── Build backend ──
echo "[build] Building backend..."
cd "$PROJECT_DIR/crm-backend"

# Copy liquibase migrations into backend resources
echo "[build] Copying liquibase migrations..."
mkdir -p src/main/resources/db/changelog
cp -r "$PROJECT_DIR/crm-liquibase/src/main/resources/db/"* src/main/resources/db/changelog/

docker build -f "$SCRIPT_DIR/Dockerfile.backend" -t "$REGISTRY/crm-backend:latest" .
docker push "$REGISTRY/crm-backend:latest"
echo "[build] Backend image pushed"

# Clean up copied liquibase files
rm -rf src/main/resources/db/changelog

# ── Build frontend ──
echo "[build] Building frontend..."
cd "$PROJECT_DIR/crm-frontend"

docker build -f "$SCRIPT_DIR/Dockerfile.frontend" \
  --build-arg VITE_API_BASE_URL=/crm/api/v1 \
  -t "$REGISTRY/crm-frontend:latest" .
docker push "$REGISTRY/crm-frontend:latest"
echo "[build] Frontend image pushed"

# ── Deploy to k3s ──
echo "[deploy] Deploying to k3s..."
cd "$SCRIPT_DIR"

kubectl create namespace "$NAMESPACE" 2>/dev/null || true

helm upgrade --install "$RELEASE" . \
  -n "$NAMESPACE" \
  -f values.prod.yaml \
  --wait --timeout 5m

echo "[deploy] Restarting deployments..."
kubectl rollout restart deployment "$RELEASE-backend" -n "$NAMESPACE"
kubectl rollout restart deployment "$RELEASE-frontend" -n "$NAMESPACE"

echo "[deploy] Checking rollout..."
kubectl rollout status deployment "$RELEASE-backend" -n "$NAMESPACE" --timeout=300s || true

echo "[deploy] Done!"
kubectl get pods -n "$NAMESPACE"
