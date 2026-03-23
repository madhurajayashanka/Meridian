# GitHub Actions CI/CD Pipeline

This directory contains the complete CI/CD pipeline for the Meridian research platform using GitHub Actions.

## Workflows

### 1. CI Pipeline (`.github/workflows/ci.yml`)

Runs on every push to `main`/`develop` branches and all pull requests.

#### Jobs:

**Backend (Spring Boot)**
- Java linting with Checkstyle
- Unit tests with JUnit 5
- Integration tests with Testcontainers
- Code coverage with JaCoCo (minimum 80%)
- JAR build and artifact upload

**AI Service (FastAPI)**
- Linting with Pylint
- Code formatting checks (black, isort)
- Property-based tests with Hypothesis
- Unit tests with pytest
- Code coverage (minimum 75%)

**Frontend (Next.js)**
- Linting with ESLint
- TypeScript type checking
- Unit tests with Vitest
- Code coverage (minimum 70%)
- Next.js build

**Container Builds** (on main branch only)
- Multi-service Docker image builds (API, AI, Frontend)
- Automatic push to GitHub Container Registry (ghcr.io)
- Cross-platform builds with Docker Buildx

**Security Scanning**
- Trivy vulnerability scanner for dependencies
- CodeQL static analysis (Java, Python, JavaScript)
- Upload results to GitHub Security tab

### 2. Deploy Pipeline (`.github/workflows/deploy.yml`)

Runs on push to `main` branch after CI passes (automatic) or manually via workflow dispatch.

#### Jobs:

**Terraform**
- Infrastructure provisioning to AWS
- `terraform plan` with artifact upload
- PR comments with plan details
- `terraform apply` on main branch
- Output export (database endpoints, Redis, S3 buckets)

**Helm Deploy**
- Kubernetes cluster connectivity via EKS
- cert-manager installation for TLS
- NGINX Ingress controller setup
- Meridian Helm chart deployment
- Rollout verification for all services

**E2E Tests**
- Playwright test suite execution
- Validates end-to-end user workflows
- Artifact upload for failed test reports

**Notifications**
- Slack webhooks for success/failure alerts
- Workflow run links for quick debugging

## Setup Instructions

### 1. GitHub Secrets Configuration

Add the following secrets to your GitHub repository:

```bash
# AWS
AWS_ROLE_TO_ASSUME=arn:aws:iam::ACCOUNT_ID:role/GitHubActionsRole

# Slack (optional)
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/YOUR/WEBHOOK/URL

# Container Registry (optional - defaults to GITHUB_TOKEN)
# REGISTRY_USERNAME=your-username
# REGISTRY_PASSWORD=your-pat-token
```

### 2. AWS IAM Role Setup

Create an IAM role for GitHub Actions with the following permissions:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "terraform:*",
        "ec2:*",
        "rds:*",
        "elasticache:*",
        "s3:*",
        "kms:*",
        "secretsmanager:*",
        "eks:*",
        "ecr:*"
      ],
      "Resource": "*"
    }
  ]
}
```

### 3. EKS Cluster Access

Configure your EKS cluster to allow GitHub Actions OIDC provider:

```bash
# Add GitHub as OIDC provider to cluster
aws eks update-cluster-config \
  --name meridian-eks-cluster \
  --logging '{"clusterLogging":[{"enabled":true,"types":["api"]}]}'
```

### 4. Terraform Backend Setup

Create S3 bucket and DynamoDB table for Terraform state:

```bash
# Create state bucket
aws s3api create-bucket \
  --bucket meridian-terraform-state \
  --region us-east-1

# Enable versioning
aws s3api put-bucket-versioning \
  --bucket meridian-terraform-state \
  --versioning-configuration Status=Enabled

# Create lock table
aws dynamodb create-table \
  --table-name terraform-lock \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST
```

### 5. Container Registry

Ensure you have access to GitHub Container Registry (ghcr.io). The pipeline uses `GITHUB_TOKEN` by default.

Optional: Create a Personal Access Token for dedicated registry access:
```bash
# Generate PAT with these scopes: read:packages, write:packages
# Add to GitHub secrets as REGISTRY_PASSWORD
```

## Pipeline Triggers

### Automatic Triggers:
- **Push to main**: Runs full CI → Container builds → Deploy to production
- **Push to develop**: Runs full CI only (no deployment)
- **Pull Request**: Runs CI pipeline (lint, test, security scan)

### Manual Triggers:
```bash
# Deploy to staging/production without code changes
gh workflow run deploy.yml -f environment=staging
gh workflow run deploy.yml -f environment=production
```

## Monitoring & Debugging

### View Workflow Status:
```bash
# List all workflows
gh workflow list

# Check latest run
gh run list

# View specific run details
gh run view <RUN_ID>

# Download artifacts
gh run download <RUN_ID>
```

### Common Issues:

**1. CodeQL timeout**
- Reduce analysis scope or increase timeout in `.github/workflows/ci.yml`

**2. Docker build failures**
- Check Dockerfile syntax: `docker build -f api/Dockerfile ./api`
- Verify dependencies in requirements.txt / package.json

**3. Terraform lock contention**
- Manually unlock: `aws dynamodb delete-item --table-name terraform-lock --key '{"LockID": {"S": "path/to/tfstate"}}'`

**4. EKS deployment failures**
- Verify cluster access: `aws eks describe-cluster --name meridian-eks-cluster`
- Check RBAC: `kubectl get clusterrolebindings`
- View pod logs: `kubectl logs -n meridian deployment/meridian-api`

## Performance Optimization

### Caching:
- ✅ Gradle cache (Java dependencies)
- ✅ pip cache (Python dependencies)  
- ✅ npm cache (Node.js dependencies)
- ✅ Docker layer caching (BuildKit)
- ✅ GitHub Actions cache (gha)

### Parallelization:
- Backend, AI service, and frontend tests run in parallel
- Container builds start after all tests pass
- Deployment waits for all containers ready

**Typical CI duration**: 8-12 minutes
**Typical deploy duration**: 15-20 minutes total

## Cost Estimation

Using **GitHub Actions free tier**:
- 2,000 free minutes/month (private repo)
- Each run: ~10-15 minutes
- Approximately 120-200 runs/month (daily + PR)
- **No cost** within free tier

For higher volume:
- $0.25 per additional minute
- Optimize with caching and matrix strategy efficiency

## Security Considerations

✅ **Implemented**:
- OIDC for AWS (no long-lived credentials)
- Container scanning with Trivy
- Static analysis with CodeQL
- Secret masking in logs
- Artifact cleanup after 5-7 days

⚠️ **Recommended**:
- Enable branch protection rules
- Require workflow approval before deployment
- Regular dependency updates (Dependabot)
- Audit IAM role permissions monthly
- Rotate secrets every 90 days

## Runbooks

### Rollback Deployment
```bash
# Last known good version
helm rollback meridian 1 -n meridian

# Verify rollback
kubectl rollout status deployment/meridian-api -n meridian
```

### Push hotfix to production
```bash
git checkout -b hotfix/issue-xyz main
# ... make fixes ...
git push origin hotfix/issue-xyz
# Create PR, merge to main
# Workflow automatically deploys
```

### Manual deployment
```bash
gh workflow run deploy.yml -f environment=production --ref main
```

## Related Documentation

- [Terraform Configuration](../infra/terraform/README.md)
- [Helm Chart Values](../../helm/values.yaml)
- [Test Coverage Requirements](../../docs/IMPLEMENTATION_STATUS.md)
- [Deployment Guide](../../docs/QUICKSTART.md)
