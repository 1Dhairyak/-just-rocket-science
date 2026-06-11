#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# jrs-platform — AWS one-time secret setup + deployment commands
#
# Run ONCE to provision secrets, then use deploy commands for updates.
# Requires: aws-cli v2, Docker, ECR access.
#
# Replace:  ACCOUNT_ID  REGION  with your values before running.
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

ACCOUNT_ID="123456789012"     # ← replace
REGION="ap-south-1"           # ← replace (Mumbai for India-deployed projects)
ECR_REPO="jrs-api-gateway"

# ── 1. Generate secrets ──────────────────────────────────────────────────────

JWT_SECRET=$(openssl rand -base64 32)
REDIS_PASSWORD=$(openssl rand -base64 16)

echo "Generated secrets (copy to a password manager NOW — not stored locally):"
echo "  JWT_SECRET:      $JWT_SECRET"
echo "  REDIS_PASSWORD:  $REDIS_PASSWORD"

# ── 2. Store in AWS Secrets Manager ─────────────────────────────────────────

aws secretsmanager create-secret \
  --name "jrs/jwt-secret" \
  --description "HS256 shared JWT signing secret — auth-service + api-gateway" \
  --secret-string "$JWT_SECRET" \
  --region "$REGION"

aws secretsmanager create-secret \
  --name "jrs/redis-password" \
  --description "ElastiCache Redis AUTH password" \
  --secret-string "$REDIS_PASSWORD" \
  --region "$REGION"

# ── 3. Store non-secret config in SSM Parameter Store ───────────────────────

# These are service-internal URIs — not sensitive, but not public either.
# Using SSM (free tier) instead of Secrets Manager (paid per secret).

aws ssm put-parameter \
  --name "/jrs/redis/host" \
  --value "jrs-redis.xxxxxx.cache.amazonaws.com" \
  --type "String" \
  --region "$REGION"

aws ssm put-parameter \
  --name "/jrs/redis/port" \
  --value "6379" \
  --type "String" \
  --region "$REGION"

aws ssm put-parameter \
  --name "/jrs/routes/auth-uri" \
  --value "http://jrs-auth-service:8081" \
  --type "String" \
  --region "$REGION"

aws ssm put-parameter \
  --name "/jrs/routes/rocket-uri" \
  --value "http://jrs-rocket-service:8082" \
  --type "String" \
  --region "$REGION"

aws ssm put-parameter \
  --name "/jrs/routes/launch-uri" \
  --value "http://jrs-launch-service:8083" \
  --type "String" \
  --region "$REGION"

aws ssm put-parameter \
  --name "/jrs/cors/allowed-origins" \
  --value "https://your-frontend-domain.com" \
  --type "String" \
  --region "$REGION"

echo "✓ Secrets and parameters provisioned."

# ─────────────────────────────────────────────────────────────────────────────
# DEPLOYMENT COMMANDS (run on every release — not one-time)
# ─────────────────────────────────────────────────────────────────────────────

GIT_SHA=$(git rev-parse --short HEAD)
BUILD_VERSION=$(git describe --tags --always)

# Build + push to ECR
build_and_push() {
  echo "→ Logging into ECR..."
  aws ecr get-login-password --region "$REGION" | \
    docker login --username AWS --password-stdin \
    "$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com"

  echo "→ Building image..."
  docker build \
    --build-arg BUILD_VERSION="$BUILD_VERSION" \
    --build-arg GIT_SHA="$GIT_SHA" \
    -t "$ECR_REPO:$GIT_SHA" \
    -t "$ECR_REPO:latest" \
    -f ../jrs-api-gateway/Dockerfile \
    ../jrs-api-gateway/

  echo "→ Tagging for ECR..."
  docker tag "$ECR_REPO:latest" \
    "$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/$ECR_REPO:$GIT_SHA"
  docker tag "$ECR_REPO:latest" \
    "$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/$ECR_REPO:latest"

  echo "→ Pushing..."
  docker push "$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/$ECR_REPO:$GIT_SHA"
  docker push "$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/$ECR_REPO:latest"

  echo "✓ Image pushed: $GIT_SHA"
}

# Update ECS service (zero-downtime rolling deploy)
deploy_ecs() {
  echo "→ Registering task definition..."
  aws ecs register-task-definition \
    --cli-input-json file://ecs-task-definition.json \
    --region "$REGION"

  echo "→ Updating ECS service..."
  aws ecs update-service \
    --cluster jrs-cluster \
    --service jrs-api-gateway \
    --task-definition jrs-api-gateway \
    --force-new-deployment \
    --region "$REGION"

  echo "→ Waiting for service stability..."
  aws ecs wait services-stable \
    --cluster jrs-cluster \
    --services jrs-api-gateway \
    --region "$REGION"

  echo "✓ Deployment complete."
}

# JWT secret rotation (both services must restart together)
rotate_jwt_secret() {
  NEW_SECRET=$(openssl rand -base64 32)
  echo "→ Rotating JWT secret..."

  aws secretsmanager put-secret-value \
    --secret-id "jrs/jwt-secret" \
    --secret-string "$NEW_SECRET" \
    --region "$REGION"

  echo "→ Restarting auth-service and gateway together..."
  aws ecs update-service --cluster jrs-cluster --service jrs-auth-service --force-new-deployment --region "$REGION"
  aws ecs update-service --cluster jrs-cluster --service jrs-api-gateway  --force-new-deployment --region "$REGION"

  echo "⚠  Existing tokens will be invalid after both services restart."
  echo "✓  Rotation complete — new secret active."
}

# ─────────────────────────────────────────────────────────────────────────────
# HEALTH CHECK COMMANDS
# ─────────────────────────────────────────────────────────────────────────────

# Local
alias health-local='curl -s http://localhost:8080/actuator/health | python3 -m json.tool'
alias health-ready='curl -s http://localhost:8080/actuator/health/readiness | python3 -m json.tool'
alias health-live='curl -s http://localhost:8080/actuator/health/liveness | python3 -m json.tool'

# Circuit breaker state
alias cb-status='curl -s http://localhost:8080/actuator/circuitbreakers | python3 -m json.tool'
