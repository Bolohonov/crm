#!/bin/bash
set -e

APP=${1:-crm-app}
TAG=${2:-latest}

VPS="root@77.222.35.2"
SERVER="michael@10.0.0.2"
REMOTE_BUILD_DIR="/home/michael/build/$APP"
REMOTE_SECRETS_DIR="/home/michael/secrets/$APP"
HELM_CHART="./crm-helm"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()  { echo -e "${GREEN}[deploy]${NC} $1"; }
warn() { echo -e "${YELLOW}[warn]${NC} $1"; }
fail() { echo -e "${RED}[error]${NC} $1"; exit 1; }

log "Starting deploy of $APP:$TAG"

[ ! -d "$HELM_CHART" ] && fail "Helm chart not found: $HELM_CHART"

log "Syncing sources to server..."
ssh -J $VPS $SERVER "mkdir -p $REMOTE_BUILD_DIR"

for SERVICE in crm-backend crm-frontend crm-liquibase; do
  if [ -d "./$SERVICE" ]; then
    log "  Syncing $SERVICE..."
    rsync -az --delete \
      --exclude='target/' \
      --exclude='node_modules/' \
      --exclude='.git/' \
      --exclude='*.class' \
      -e "ssh -J $VPS" \
      ./$SERVICE/ $SERVER:$REMOTE_BUILD_DIR/$SERVICE/
  fi
done

log "Building images on server..."
ssh -J $VPS $SERVER bash << REMOTEBUILD
set -e
REGISTRY="localhost:5000"
TAG="$TAG"
BUILD_DIR="$REMOTE_BUILD_DIR"

echo "[server] Copying liquibase migrations into backend..."
cp -r \$BUILD_DIR/crm-liquibase/src/main/resources/db/. \$BUILD_DIR/crm-backend/src/main/resources/db/

echo "[server] Building crm-backend..."
docker build -t \$REGISTRY/crm-backend:\$TAG \$BUILD_DIR/crm-backend/

echo "[server] Cleaning up liquibase copy..."
rm -rf \$BUILD_DIR/crm-backend/src/main/resources/db/migration

echo "[server] Building crm-frontend..."
docker build -t \$REGISTRY/crm-frontend:\$TAG \
  --build-arg VITE_API_BASE_URL=/crm/api/v1 \
  \$BUILD_DIR/crm-frontend/

echo "[server] Pushing to local registry..."
docker push \$REGISTRY/crm-backend:\$TAG
docker push \$REGISTRY/crm-frontend:\$TAG
echo "[server] Done building"
REMOTEBUILD

log "Syncing helm chart..."
ssh -J $VPS $SERVER "mkdir -p $REMOTE_BUILD_DIR/helm"
rsync -az --delete \
  --exclude='values.prod.yaml' \
  -e "ssh -J $VPS" \
  $HELM_CHART/ $SERVER:$REMOTE_BUILD_DIR/helm/

log "Deploying to k3s..."
ssh -J $VPS $SERVER bash << REMOTEDEPLOY
set -e
SECRETS="$REMOTE_SECRETS_DIR/values.prod.yaml"
CHART="$REMOTE_BUILD_DIR/helm"

[ ! -f "\$SECRETS" ] && echo "ERROR: values.prod.yaml not found at \$SECRETS" && exit 1

echo "[server] Clearing k3s image cache..."
sudo crictl rmi localhost:5000/crm-backend:$TAG 2>/dev/null || true
sudo crictl rmi localhost:5000/crm-frontend:$TAG 2>/dev/null || true

helm upgrade --install $APP \$CHART \
  --namespace crm --create-namespace \
  --values \$SECRETS \
  --set backend.image.tag=$TAG \
  --set frontend.image.tag=$TAG

echo "[server] Restarting deployments..."
kubectl -n crm rollout restart deployment/$APP-backend
kubectl -n crm rollout restart deployment/$APP-frontend

echo "[server] Checking rollout..."
kubectl -n crm rollout status deployment/$APP-backend --timeout=300s
kubectl -n crm rollout status deployment/$APP-frontend --timeout=120s
echo "[server] Deploy complete"
REMOTEDEPLOY

log "Deploy of $APP:$TAG complete"