#!/usr/bin/env bash
set -euo pipefail

# Deploy the whole saga to GCP:
#   3 Cloud Run services (order, payment, inventory) + RabbitMQ/Postgres on one e2-micro VM.
#
# Usage:
#   ./scripts/deploy.sh <PROJECT_ID> [--create]
#   GCP_PROJECT_ID=... GCP_REGION=... ./scripts/deploy.sh <PROJECT_ID>
#
# Env vars honoured: GCP_REGION, GCP_ZONE, BILLING_ACCOUNT (for --create),
#                    RABBITMQ_USER/PASSWORD, POSTGRES_USER/PASSWORD/DB.

PROJECT_ID="${1:-${GCP_PROJECT_ID:-}}"
FLAG="${2:-}"
REGION="${GCP_REGION:-southamerica-east1}"
ZONE="${GCP_ZONE:-southamerica-east1-a}"

if [ -z "$PROJECT_ID" ]; then
  echo "usage: deploy.sh <PROJECT_ID> [--create]" >&2
  exit 1
fi

RABBIT_USER="${RABBITMQ_USER:-saga}"
RABBIT_PASS="${RABBITMQ_PASSWORD:-saga}"
PG_USER="${POSTGRES_USER:-saga}"
PG_PASS="${POSTGRES_PASSWORD:-saga}"
PG_DB="${POSTGRES_DB:-saga}"

if [ "$FLAG" = "--create" ]; then
  echo "== Creating project $PROJECT_ID =="
  gcloud projects create "$PROJECT_ID" --name="spring-rabbit-saga"
  if [ -n "${BILLING_ACCOUNT:-}" ]; then
    gcloud billing projects link "$PROJECT_ID" --billing-account="$BILLING_ACCOUNT"
  else
    echo "WARN: no BILLING_ACCOUNT set — link one manually:"
    echo "  gcloud billing projects link $PROJECT_ID --billing-account=XXXXXX-XXXXXX-XXXXXX"
  fi
fi

gcloud config set project "$PROJECT_ID"

echo "== Enabling APIs =="
gcloud services enable run.googleapis.com artifactregistry.googleapis.com cloudbuild.googleapis.com compute.googleapis.com --project "$PROJECT_ID"

echo "== Provisioning broker VM (RabbitMQ + Postgres via startup script) =="
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
gcloud compute instances create broker \
  --project "$PROJECT_ID" --zone "$ZONE" --machine-type e2-micro \
  --image-project debian-cloud --image-family debian-12 \
  --metadata-from-file startup-script="$SCRIPT_DIR/broker-startup.sh" \
  --tags broker

echo "== Opening broker firewall (demo posture: world-readable, see README) =="
gcloud compute firewall-rules create allow-broker \
  --project "$PROJECT_ID" --allow tcp:5432,tcp:5672,tcp:15672 \
  --target-tags broker --source-ranges 0.0.0.0/0

BROKER_IP=$(gcloud compute instances describe broker --project "$PROJECT_ID" --zone "$ZONE" --format='value(networkInterfaces[0].accessConfigs[0].natIP)')
echo "broker IP: $BROKER_IP"
echo "(containers finish starting ~2-3 min after the VM boots)"

COMMON_ENV="SPRING_RABBITMQ_HOST=$BROKER_IP,SPRING_RABBITMQ_PORT=5672,SPRING_RABBITMQ_USERNAME=$RABBIT_USER,SPRING_RABBITMQ_PASSWORD=$RABBIT_PASS"

echo "== Building + deploying order-service =="
gcloud builds submit order-service --tag "gcr.io/$PROJECT_ID/order-service" --project "$PROJECT_ID"
gcloud run deploy order-service \
  --image "gcr.io/$PROJECT_ID/order-service" --region "$REGION" --project "$PROJECT_ID" \
  --platform managed --allow-unauthenticated --no-cpu-throttling --min-instances 1 \
  --set-env-vars "$COMMON_ENV,SPRING_DATASOURCE_URL=jdbc:postgresql://$BROKER_IP:5432/$PG_DB,SPRING_DATASOURCE_USERNAME=$PG_USER,SPRING_DATASOURCE_PASSWORD=$PG_PASS"

echo "== Building + deploying payment-service =="
gcloud builds submit payment-service --tag "gcr.io/$PROJECT_ID/payment-service" --project "$PROJECT_ID"
gcloud run deploy payment-service \
  --image "gcr.io/$PROJECT_ID/payment-service" --region "$REGION" --project "$PROJECT_ID" \
  --platform managed --allow-unauthenticated --no-cpu-throttling --min-instances 1 --set-env-vars "$COMMON_ENV"

echo "== Building + deploying inventory-service =="
gcloud builds submit inventory-service --tag "gcr.io/$PROJECT_ID/inventory-service" --project "$PROJECT_ID"
gcloud run deploy inventory-service \
  --image "gcr.io/$PROJECT_ID/inventory-service" --region "$REGION" --project "$PROJECT_ID" \
  --platform managed --allow-unauthenticated --no-cpu-throttling --min-instances 1 --set-env-vars "$COMMON_ENV"

ORDER_URL=$(gcloud run services describe order-service --project "$PROJECT_ID" --region "$REGION" --format='value(status.url)')

echo ""
echo "== Deployed =="
echo "Order service: $ORDER_URL"
echo "Submit an order (happy path):"
echo "  curl -X POST $ORDER_URL/orders -H 'Content-Type: application/json' -d '{\"customerId\":\"c1\",\"amount\":100}'"
echo "Compensation path (amount > 10000 triggers payment decline):"
echo "  curl -X POST $ORDER_URL/orders -H 'Content-Type: application/json' -d '{\"customerId\":\"c2\",\"amount\":20000}'"
