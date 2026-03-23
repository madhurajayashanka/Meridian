# Meridian Terraform Infrastructure

This directory contains Terraform configuration to deploy the Meridian platform on AWS.

## Architecture

The Terraform configuration creates the following infrastructure:

### Networking

- **VPC**: Custom VPC with configurable CIDR block
- **Subnets**: 3 public and 3 private subnets across availability zones
- **NAT Gateway**: For private subnet internet access
- **Internet Gateway**: For public subnet internet access
- **Route Tables**: Proper routing for public and private subnets

### Database

- **RDS PostgreSQL**: Managed PostgreSQL 15 database
  - Multi-AZ deployment for high availability
  - Automated backups with 30-day retention
  - Performance Insights enabled
  - Enhanced monitoring with CloudWatch
  - pgvector support for semantic search

### Cache Layer

- **ElastiCache Redis**: Managed Redis cluster
  - Multi-AZ with automatic failover
  - At-rest and in-transit encryption
  - Auth token protection
  - Automated backups with 5-day retention
  - CloudWatch alarms for CPU, memory, and evictions

### Storage

- **S3 Documents Bucket**: For uploaded research documents
  - Versioning enabled
  - KMS encryption
  - Lifecycle policies for old versions
  - CORS configuration

- **S3 Reports Bucket**: For generated research reports
  - Versioning enabled
  - KMS encryption
  - Access logging

- **S3 Logs Bucket**: For application and access logs
  - Automatic cleanup with 90-day retention

### Security

- **Security Groups**: Properly configured for database, cache, and application layers
- **KMS Keys**: For encryption at rest
- **IAM Roles**: For service access (RDS monitoring, EKS pods, EC2 instances)
- **AWS Secrets Manager**: For storing database and cache credentials

## Prerequisites

1. **AWS Account**: With appropriate permissions
2. **Terraform**: Version 1.0 or later
3. **AWS CLI**: Configured with credentials
4. **Terraform Backend**: S3 bucket and DynamoDB table for state management

### Create Terraform Backend

```bash
# Create S3 bucket for state
aws s3api create-bucket \
  --bucket meridian-terraform-state \
  --region us-east-1

# Enable versioning
aws s3api put-bucket-versioning \
  --bucket meridian-terraform-state \
  --versioning-configuration Status=Enabled

# Create DynamoDB table for state locking
aws dynamodb create-table \
  --table-name terraform-lock \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region us-east-1
```

## Setup

1. **Clone the repository**:

```bash
cd infra/terraform
```

2. **Create terraform.tfvars**:

```bash
cp terraform.tfvars.example terraform.tfvars
# Edit with your desired values
vim terraform.tfvars
```

3. **Set RDS password environment variable** (recommended):

```bash
export TF_VAR_rds_password="YourSecurePassword123"
```

4. **Initialize Terraform**:

```bash
terraform init
```

5. **Validate configuration**:

```bash
terraform validate
```

## Usage

### Plan Changes

```bash
terraform plan -out=tfplan
```

### Apply Changes

```bash
terraform apply tfplan
```

### Destroy Infrastructure

```bash
terraform destroy
```

## Variables

Key variables you should customize:

- `aws_region`: AWS region (default: us-east-1)
- `environment`: Environment name (development, staging, production)
- `vpc_cidr`: VPC CIDR block (default: 10.0.0.0/16)
- `rds_db_name`: Database name (default: meridiandb)
- `rds_username`: Database admin user (default: meridianadmin)
- `rds_password`: Database password (MUST be changed!)
- `rds_allocated_storage`: Database size in GB (default: 100)
- `redis_num_cache_nodes`: Number of Redis nodes (default: 2)
- `eks_desired_size`: Desired EKS worker nodes (default: 3)

## Outputs

After applying, retrieve outputs with:

```bash
# Get all outputs
terraform output

# Get specific output
terraform output rds_endpoint
terraform output redis_endpoint
terraform output documents_bucket_name

# Get sensitive outputs
terraform output -json | jq .rds_connection_string.value
```

Key outputs:

- `rds_endpoint`: PostgreSQL endpoint
- `redis_endpoint`: Redis endpoint
- `documents_bucket_name`: S3 bucket for documents
- `reports_bucket_name`: S3 bucket for reports

## Security Best Practices

1. **Never commit terraform.tfvars** with actual passwords
2. **Use AWS Secrets Manager** for sensitive values
3. **Enable encryption** at rest and in transit (already configured)
4. **Restrict security group** ingress rules to your IP/CIDR
5. **Enable MFA delete** on S3 buckets in production
6. **Use CloudTrail** for auditing infrastructure changes

## Cost Optimization

To reduce costs in non-production environments:

1. Use smaller RDS instance class: `db.t4g.micro` or `db.t4g.small`
2. Reduce allocated storage: `20` GB
3. Use single-AZ RDS: Set `multi_az = false`
4. Use single-node Redis: `redis_num_cache_nodes = 1`
5. Disable backups in development: `backup_retention_period = 0`

## Monitoring

CloudWatch dashboards and alarms are configured for:

- RDS CPU, connections, disk space
- Redis CPU, memory, evictions
- S3 bucket sizes and object counts
- VPC network traffic

## Troubleshooting

### State Lock Issues

If you encounter state lock issues:

```bash
terraform force-unlock <LOCK_ID>
```

### Terraform Apply Fails

1. Check AWS credentials: `aws sts get-caller-identity`
2. Verify permissions for creating resources
3. Check for conflicting resources in AWS account
4. Review error logs: `terraform apply -lock=false`

### Connection Issues

Test database connectivity:

```bash
# Get RDS endpoint
RDS_ENDPOINT=$(terraform output -raw rds_address)

# Connect via psql
psql -h $RDS_ENDPOINT -U meridianadmin -d meridiandb
```

Test Redis connectivity:

```bash
# Get Redis endpoint
REDIS_ENDPOINT=$(terraform output -raw redis_endpoint)

# Connect via redis-cli (if installed)
redis-cli -h $REDIS_ENDPOINT -p 6379 PING
```

## Next Steps

After infrastructure is deployed:

1. **Deploy Applications**: Use Helm charts (see `/helm`)
2. **Configure CI/CD**: Set up GitHub Actions (see `/.github/workflows`)
3. **Setup Monitoring**: Configure CloudWatch dashboards
4. **Backup Strategy**: Configure automated backups and disaster recovery
5. **Scaling Policies**: Configure auto-scaling for RDS and EKS

## Support

For issues or questions:

1. Check Terraform documentation: https://www.terraform.io/docs
2. Check AWS documentation: https://docs.aws.amazon.com
3. Review CloudWatch Logs for application errors
4. Check AWS Systems Manager Session Manager for accessing EC2 instances

## License

This Terraform configuration is part of the Meridian project.
