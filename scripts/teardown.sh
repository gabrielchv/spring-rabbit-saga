#!/usr/bin/env bash
set -euo pipefail

# Tear down everything deploy.sh created. Does NOT delete the GCP project.
#
# Usage: ./scripts/teardown.sh <PROJECT_ID>

PROJECT_ID="${1:-${GCP_PROJECT_ID:-}}"
REGION="${GCP_REGION:-southamerica-east1}"
ZONE="${GCP_ZONE:-southamerica-east1-a}"

if [ -z "$PROJECT_ID" ]; then
  echo "usage: teardown.sh <PROJECT_ID>" >&2
  exit 1
fi

gcloud config set project "$PROJECT_ID"

for svc in order-service payment-service inventory-service; do
  gcloud run services delete "$svc" --region "$REGION" --project "$PROJECT_ID" --quiet || true
done

gcloud compute instances delete rabbitmq postgres --zone "$ZONE" --project "$PROJECT_ID" --quiet || true
gcloud compute firewall-rules delete allow-broker --project "$PROJECT_ID" --quiet || true

for svc in order-service payment-service inventory-service; do
  gcloud container images delete "gcr.io/$PROJECT_ID/$svc" --project "$PROJECT_ID" --quiet || true
done

echo ""
echo "Broker VMs, firewall and Cloud Run services removed."
echo "To delete the project entirely: gcloud projects delete $PROJECT_ID"
