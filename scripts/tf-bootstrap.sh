#!/usr/bin/env bash
# scripts/tf-bootstrap.sh
# Creates the S3 state bucket + DynamoDB lock table, then writes backend.hcl
# for each environment. Run once per AWS account.
#
# Usage: ./scripts/tf-bootstrap.sh [region] [bucket-name]
set -euo pipefail

REGION="${1:-us-east-1}"
BUCKET="${2:-meridian-terraform-state}"
TABLE="meridian-terraform-lock"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENVS_DIR="$SCRIPT_DIR/../infra/terraform/environments"

echo "Bootstrapping Terraform state backend..."
echo "  Region : $REGION"
echo "  Bucket : $BUCKET"
echo "  Table  : $TABLE"

# Create S3 bucket
if [ "$REGION" = "us-east-1" ]; then
  aws s3api create-bucket --bucket "$BUCKET" --region "$REGION" 2>/dev/null || true
else
  aws s3api create-bucket --bucket "$BUCKET" --region "$REGION" \
    --create-bucket-configuration LocationConstraint="$REGION" 2>/dev/null || true
fi

aws s3api put-bucket-versioning --bucket "$BUCKET" \
  --versioning-configuration Status=Enabled

aws s3api put-bucket-encryption --bucket "$BUCKET" \
  --server-side-encryption-configuration \
  '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'

aws s3api put-public-access-block --bucket "$BUCKET" \
  --public-access-block-configuration \
  "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"

# Create DynamoDB lock table
aws dynamodb create-table \
  --table-name "$TABLE" \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region "$REGION" 2>/dev/null || true

# Write backend.hcl for each environment
for ENV in dev staging prod; do
  ENV_DIR="$ENVS_DIR/$ENV"
  mkdir -p "$ENV_DIR"
  cat > "$ENV_DIR/backend.hcl" <<HCL
bucket         = "$BUCKET"
key            = "$ENV/terraform.tfstate"
region         = "$REGION"
encrypt        = true
dynamodb_table = "$TABLE"
HCL
  echo "  ✓ Wrote $ENV_DIR/backend.hcl"
done

echo ""
echo "✓ Bootstrap complete. Next steps:"
echo "  export TF_VAR_rds_password='<strong-password>'"
echo "  export TF_VAR_app_domain='yourdomain.com'"
echo "  make aws-apply TF_ENV=dev"
