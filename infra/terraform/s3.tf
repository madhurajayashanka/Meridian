# S3 Bucket for Documents
resource "aws_s3_bucket" "documents" {
  bucket              = "${var.s3_document_bucket_name}-${data.aws_caller_identity.current.account_id}"
  force_destroy       = false

  tags = {
    Name = "${var.app_name}-documents"
  }
}

# Enable versioning for documents bucket
resource "aws_s3_bucket_versioning" "documents" {
  bucket = aws_s3_bucket.documents.id

  versioning_configuration {
    status = "Enabled"
  }
}

# Enable server-side encryption for documents
resource "aws_s3_bucket_server_side_encryption_configuration" "documents" {
  bucket = aws_s3_bucket.documents.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.s3.arn
    }
    bucket_key_enabled = true
  }
}

# Block public access to documents
resource "aws_s3_bucket_public_access_block" "documents" {
  bucket = aws_s3_bucket.documents.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Lifecycle policy for documents
resource "aws_s3_bucket_lifecycle_configuration" "documents" {
  bucket = aws_s3_bucket.documents.id

  rule {
    id     = "expire-old-versions"
    status = "Enabled"

    noncurrent_version_transition {
      noncurrent_days = 30
      storage_class   = "GLACIER"
    }

    noncurrent_version_expiration {
      noncurrent_days = 90
    }
  }

  rule {
    id     = "expire-incomplete-uploads"
    status = "Enabled"

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}

# CORS configuration for documents
resource "aws_s3_bucket_cors_configuration" "documents" {
  bucket = aws_s3_bucket.documents.id

  cors_rule {
    allowed_headers = ["*"]
    allowed_methods = ["GET", "PUT", "POST"]
    allowed_origins = var.app_domain != "" ? ["https://${var.app_domain}", "https://api.${var.app_domain}"] : ["*"]
    expose_headers  = ["ETag"]
    max_age_seconds = 3000
  }
}

# S3 Bucket for Reports
resource "aws_s3_bucket" "reports" {
  bucket        = "${var.s3_reports_bucket_name}-${data.aws_caller_identity.current.account_id}"
  force_destroy = false

  tags = {
    Name = "${var.app_name}-reports"
  }
}

# Enable versioning for reports bucket
resource "aws_s3_bucket_versioning" "reports" {
  bucket = aws_s3_bucket.reports.id

  versioning_configuration {
    status = "Enabled"
  }
}

# Enable server-side encryption for reports
resource "aws_s3_bucket_server_side_encryption_configuration" "reports" {
  bucket = aws_s3_bucket.reports.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.s3.arn
    }
    bucket_key_enabled = true
  }
}

# Block public access to reports
resource "aws_s3_bucket_public_access_block" "reports" {
  bucket = aws_s3_bucket.reports.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Enable logging for reports (optional)
resource "aws_s3_bucket_logging" "reports" {
  bucket = aws_s3_bucket.reports.id

  target_bucket = aws_s3_bucket.logs.id
  target_prefix = "s3-reports-logs/"
}

# S3 Bucket for Logs
resource "aws_s3_bucket" "logs" {
  bucket        = "${var.app_name}-logs-${data.aws_caller_identity.current.account_id}"
  force_destroy = false

  tags = {
    Name = "${var.app_name}-logs"
  }
}

# Block public access to logs
resource "aws_s3_bucket_public_access_block" "logs" {
  bucket = aws_s3_bucket.logs.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Lifecycle policy for logs
resource "aws_s3_bucket_lifecycle_configuration" "logs" {
  bucket = aws_s3_bucket.logs.id

  rule {
    id     = "delete-old-logs"
    status = "Enabled"

    expiration {
      days = 90
    }
  }
}

# KMS Key for S3 Encryption
resource "aws_kms_key" "s3" {
  description             = "KMS key for S3 encryption"
  deletion_window_in_days = 10
  enable_key_rotation     = true

  tags = {
    Name = "${var.app_name}-s3-key"
  }
}

resource "aws_kms_alias" "s3" {
  name          = "alias/${var.app_name}-s3"
  target_key_id = aws_kms_key.s3.key_id
}

# IAM Policy for S3 Access
resource "aws_iam_policy" "s3_access" {
  name        = "${var.app_name}-s3-access"
  description = "Policy for S3 bucket access"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject",
          "s3:ListBucket"
        ]
        Resource = [
          aws_s3_bucket.documents.arn,
          "${aws_s3_bucket.documents.arn}/*",
          aws_s3_bucket.reports.arn,
          "${aws_s3_bucket.reports.arn}/*"
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "kms:Decrypt",
          "kms:GenerateDataKey",
          "kms:DescribeKey"
        ]
        Resource = [aws_kms_key.s3.arn]
      }
    ]
  })

  tags = {
    Name = "${var.app_name}-s3-access"
  }
}

# Data source for current AWS account
data "aws_caller_identity" "current" {}
