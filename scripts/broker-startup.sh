#!/bin/bash
set -euxo pipefail
export DEBIAN_FRONTEND=noninteractive

# Install Docker (official Debian repo)
apt-get update -qq
apt-get install -y -qq ca-certificates curl gnupg
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $(. /etc/os-release && echo "$VERSION_CODENAME") stable" > /etc/apt/sources.list.d/docker.list
apt-get update -qq
apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-compose-plugin
systemctl enable --now docker

docker network create broker || true

# Postgres — demo credentials, see README security note
docker run -d --restart unless-stopped --name postgres --network broker \
  -e POSTGRES_DB=saga -e POSTGRES_USER=saga -e POSTGRES_PASSWORD=saga \
  -p 5432:5432 postgres:16

# RabbitMQ with management UI
docker run -d --restart unless-stopped --name rabbitmq --network broker \
  -e RABBITMQ_DEFAULT_USER=saga -e RABBITMQ_DEFAULT_PASS=saga \
  -p 5672:5672 -p 15672:15672 rabbitmq:3-management
