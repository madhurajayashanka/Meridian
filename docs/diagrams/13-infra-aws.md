# 13 — AWS Infrastructure

All AWS resources, networking topology, and how they connect.

---

## Diagram: AWS Network Topology

```
┌─────────────────────────────────────────────────────────────────────┐
│  AWS Region: us-east-1                                               │
│                                                                      │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  VPC: 10.0.0.0/16                                             │  │
│  │                                                               │  │
│  │  ┌─────────────────────────────────────────────────────────┐ │  │
│  │  │  Public Subnets (3 AZs: 10.0.101-103.0/24)              │ │  │
│  │  │                                                          │ │  │
│  │  │  ┌──────────────┐    ┌──────────────┐                   │ │  │
│  │  │  │ Internet GW  │    │  NAT Gateway │                   │ │  │
│  │  │  └──────┬───────┘    └──────┬───────┘                   │ │  │
│  │  └─────────┼────────────────────┼────────────────────────── ┘ │  │
│  │            │ internet           │ outbound only                │  │
│  │  ┌─────────┼────────────────────┼────────────────────────────┐ │  │
│  │  │  Private Subnets (3 AZs: 10.0.1-3.0/24)                  │ │  │
│  │  │         │                    │                            │ │  │
│  │  │  ┌──────▼──────┐    ┌────────▼──────────────────────┐   │ │  │
│  │  │  │  EKS Cluster│    │  EKS Worker Nodes             │   │ │  │
│  │  │  │  (control   │    │  (t3.medium, 2-10 nodes)      │   │ │  │
│  │  │  │   plane)    │    │  Pods:                        │   │ │  │
│  │  │  └─────────────┘    │  • meridian-frontend          │   │ │  │
│  │  │                     │  • meridian-api               │   │ │  │
│  │  │                     │  • meridian-ai (IRSA)         │   │ │  │
│  │  │                     └───────────────────────────────┘   │ │  │
│  │  │                                                          │ │  │
│  │  │  ┌──────────────────┐    ┌──────────────────────────┐   │ │  │
│  │  │  │  RDS PostgreSQL  │    │  ElastiCache Redis       │   │ │  │
│  │  │  │  (db.t4g.medium) │    │  (cache.t4g.small)       │   │ │  │
│  │  │  │  encrypted       │    │  encrypted (KMS)         │   │ │  │
│  │  │  │  Multi-AZ        │    │  Multi-AZ                │   │ │  │
│  │  │  └──────────────────┘    └──────────────────────────┘   │ │  │
│  │  └──────────────────────────────────────────────────────────┘ │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  S3 Buckets (regional, not in VPC):                                  │
│    meridian-documents   meridian-reports   meridian-terraform-state  │
│                                                                      │
│  External Services (accessed via NAT):                               │
│    AWS Bedrock (Claude)   Tavily API   GitHub Container Registry     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Diagram: Security Group Rules

```
ingress-sg (ALB / Nginx):
  INBOUND:  80  TCP  0.0.0.0/0   (HTTP → redirect to HTTPS)
  INBOUND:  443 TCP  0.0.0.0/0   (HTTPS)
  INBOUND:  3000 TCP VPC CIDR    (frontend, internal only)
  INBOUND:  8000-8080 TCP VPC CIDR (API/AI, internal only)
  OUTBOUND: all → 0.0.0.0/0

rds-sg:
  INBOUND:  5432 TCP  EKS nodes SG only
  OUTBOUND: none

redis-sg:
  INBOUND:  6379 TCP  EKS nodes SG only
  OUTBOUND: none

eks-nodes-sg:
  INBOUND:  all from within SG (pod-to-pod)
  OUTBOUND: all → 0.0.0.0/0 (via NAT)
```

---

## Diagram: IRSA (Pod-Level AWS Permissions)

```
meridian-ai pod
    │
    │ uses ServiceAccount: meridian-ai
    │ annotated: eks.amazonaws.com/role-arn = arn:aws:iam::ACCOUNT:role/meridian-ai-service-irsa
    │
    ▼
OIDC Provider (EKS cluster OIDC URL)
    │
    ▼
IAM Role: meridian-ai-service-irsa
  Trust policy: allows meridian-ai ServiceAccount to assume role
  Permissions:
    bedrock:InvokeModel
    bedrock:InvokeModelWithResponseStream
    s3:GetObject, PutObject, DeleteObject
      on: meridian-reports/* and meridian-documents/*

EKS worker nodes: NO AWS permissions (no instance profile)
```

---

## Diagram: Terraform Resource Map

```
main.tf          → provider, backend (S3 + DynamoDB)
vpc.tf           → VPC, subnets, IGW, NAT, route tables, SGs
rds.tf           → RDS instance, subnet group, parameter group,
                   Secrets Manager secret, CloudWatch logs
redis.tf         → ElastiCache cluster, subnet group, KMS key,
                   SNS topic, CloudWatch alarms
s3.tf            → documents bucket, reports bucket, policies
eks.tf           → EKS cluster, node group, IAM roles,
                   OIDC provider, IRSA role for AI service
outputs.tf       → VPC ID, RDS endpoint, Redis endpoint,
                   EKS cluster name, IRSA role ARN
variables.tf     → all configurable values with defaults

environments/
  dev/main.tf    → S3 backend key: dev/terraform.tfstate
  staging/main.tf→ S3 backend key: staging/terraform.tfstate
  prod/main.tf   → S3 backend key: prod/terraform.tfstate
```
