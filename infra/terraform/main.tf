terraform {
  required_version = ">= 1.0"
  
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # State bucket and key are passed via -backend-config at init time.
  # Run: terraform init -backend-config=backend.hcl
  # See scripts/tf-bootstrap.sh to create the bucket + lock table first.
  backend "s3" {}
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Environment = var.environment
      Project     = "meridian"
      ManagedBy   = "terraform"
    }
  }
}
