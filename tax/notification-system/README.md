# Infrastructure for Notification System

## Architecture Overview

The system is built as a cloud-native microservice architecture:

1.  **API Layer**: Spring Boot / Kotlin services for preference management and event ingestion.
2.  **Event Streaming**: Apache Kafka for high-throughput, low-latency event propagation.
3.  **Caching/Deduplication**: Redis for idempotency checks and rate-limiting.
4.  **Storage**: PostgreSQL with TimescaleDB extension for audit logs and real-time analytics.
5.  **Observability**: Prometheus for metrics, Grafana for SLA dashboards, and Jaeger for distributed tracing.

## Kubernetes Deployment (k8s/notification-service.yaml)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: notification-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: notification-service
  template:
    metadata:
      labels:
        app: notification-service
    spec:
      containers:
      - name: notification-service
        image: notification-service:latest
        ports:
        - containerPort: 8080
        env:
        - name: KAFKA_BOOTSTRAP_SERVERS
          value: "kafka:9092"
        - name: REDIS_URL
          value: "redis://redis:6379"
        - name: DB_URL
          value: "jdbc:postgresql://timescaledb:5432/notifications"
        resources:
          limits:
            cpu: "1"
            memory: "1Gi"
          requests:
            cpu: "500m"
            memory: "512Mi"
```

## Terraform (terraform/main.tf)

```hcl
resource "kubernetes_namespace" "notifications" {
  metadata {
    name = "notifications"
  }
}

module "kafka_cluster" {
  source = "./modules/kafka"
  cluster_name = "notification-streaming"
  namespace    = kubernetes_namespace.notifications.metadata[0].name
}

module "redis_cluster" {
  source = "./modules/redis"
  cluster_name = "notification-cache"
  namespace    = kubernetes_namespace.notifications.metadata[0].name
}
```
