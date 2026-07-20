#!/bin/bash
set -e

# Pre-creates the Note domain-event topics for mod-notes so they are visible in Kafka UI straight away
KAFKA_BROKER="${KAFKA_HOST}:${KAFKA_PORT}"
echo "Waiting for Kafka broker at $KAFKA_BROKER..."

until /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server "$KAFKA_BROKER" > /dev/null 2>&1; do
  echo "Kafka broker not ready yet, waiting..."
  sleep 2
done

echo "Kafka broker is ready!"

KAFKA_TOPICS_CMD="/opt/kafka/bin/kafka-topics.sh"

# Comma-separated list of tenants (from the TENANTS env var), default "diku,test".
IFS=',' read -ra TENANT_LIST <<< "${TENANTS:-diku,test}"

for TENANT in "${TENANT_LIST[@]}"; do
  TENANT_TRIMMED=$(echo "$TENANT" | xargs)
  TOPIC="${ENV}.${TENANT_TRIMMED}.notes.note"
  $KAFKA_TOPICS_CMD \
    --create \
    --bootstrap-server "$KAFKA_BROKER" \
    --replication-factor 1 \
    --partitions "${KAFKA_TOPIC_PARTITIONS}" \
    --topic "$TOPIC" \
    --if-not-exists
  echo "Created topic: $TOPIC"
done

echo "Kafka topics created successfully."

