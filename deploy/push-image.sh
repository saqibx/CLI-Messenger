#!/usr/bin/env bash

set -euo pipefail
cd "$(dirname "$0")"

REGION="${AWS_REGION:-us-east-1}"

REPO="$(terraform -chdir=terraform output -raw ecr_repository_url)"
REGISTRY="${REPO%%/*}"

aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRY"

docker build --platform linux/arm64 -t "$REPO:latest" ..
docker push "$REPO:latest"

aws ecs update-service --cluster devchat --service devchat --force-new-deployment --region "$REGION" >/dev/null

echo "pushed $REPO:latest and triggered a new deployment"
