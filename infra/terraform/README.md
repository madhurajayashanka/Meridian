# Meridian Terraform Infrastructure

Provisions all AWS resources for the Meridian platform.

## Resources Created

- **VPC** — custom CIDR, 3 public + 3 private subnets, NAT gateway
- **RDS PostgreSQL 15** — encrypted at rest, pgvector extension, Secrets Manager credentials
- **ElastiCache Redis 7** — encrypted at rest (KMS) + in-transit, auth token
- **S3** — two buckets: documents and reports
- **EKS** — cluster + managed node group, IRSA for AI service pods
- **IAM** — least-privilege roles for EKS nodes, RDS monitoring, AI service (Bedrock + S3)
- **Secrets Manager** — RDS and Redis credentials

## Environments

Each environment has isolated Terraform state:

```
environments/
├── dev/      # db.t4g.small, 1 Redis node, 1-4 EKS nodes
├── staging/  # db.t4g.medium, 1 Redis node, 2-6 EKS nodes
└── prod/     # db.t4g.medium, 2 Redis nodes, 2-10 EKS nodes
```

## Usage

```bash
# Plan
make aws-plan TF_ENV=prod

# Apply
make aws-apply TF_ENV=prod

# Destroy (irreversible — prompts for confirmation)
make aws-destroy TF_ENV=prod
```

## State Backend

State is stored in S3 with DynamoDB locking. Create these before first `terraform init`:

```bash
aws s3 mb s3://meridian-terraform-state
aws dynamodb create-table \
  --table-name terraform-lock \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST
```

## Key Variables

| Variable | Description | Default |
|---|---|---|
| `aws_region` | AWS region | `us-east-1` |
| `eks_public_access_cidrs` | CIDRs for EKS API access — **restrict to VPN in prod** | `0.0.0.0/0` |
| `rds_instance_class` | RDS instance type | `db.t4g.medium` |
| `eks_desired_size` | EKS node count | `3` |
