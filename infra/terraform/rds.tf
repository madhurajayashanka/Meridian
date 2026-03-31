# DB Subnet Group
resource "aws_db_subnet_group" "main" {
  name       = "${var.app_name}-db-subnet-group"
  subnet_ids = aws_subnet.private[*].id

  tags = {
    Name = "${var.app_name}-db-subnet-group"
  }
}

# RDS PostgreSQL Instance
resource "aws_db_instance" "main" {
  identifier            = "${var.app_name}-postgres"
  engine                = "postgres"
  engine_version        = var.rds_engine_version
  instance_class        = var.rds_instance_class
  allocated_storage     = var.rds_allocated_storage
  storage_type          = "gp3"
  db_name               = var.rds_db_name
  username              = var.rds_username
  password              = var.rds_password
  db_subnet_group_name  = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  
  # Backup settings
  backup_retention_period = 30
  backup_window           = "03:00-04:00"
  
  # Maintenance
  maintenance_window = "mon:04:00-mon:05:00"
  
  # Security
  publicly_accessible    = false
  storage_encrypted      = true
  ssl_certificate_identifier = var.rds_ca_identifier
  
  # Performance Insights
  performance_insights_enabled          = true
  performance_insights_retention_period = 7
  
  # Enhanced monitoring
  enabled_cloudwatch_logs_exports = ["postgresql"]
  monitoring_interval             = 60
  monitoring_role_arn             = aws_iam_role.rds_monitoring.arn
  
  # Deletion protection
  deletion_protection = true
  skip_final_snapshot = false
  final_snapshot_identifier = "${var.app_name}-postgres-final-snapshot-${formatdate("YYYY-MM-DD-hhmm", timestamp())}"
  
  tags = {
    Name = "${var.app_name}-postgres"
  }
}

# IAM Role for RDS Enhanced Monitoring
resource "aws_iam_role" "rds_monitoring" {
  name = "${var.app_name}-rds-monitoring-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "monitoring.rds.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name = "${var.app_name}-rds-monitoring-role"
  }
}

resource "aws_iam_role_policy_attachment" "rds_monitoring" {
  role       = aws_iam_role.rds_monitoring.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}

# CloudWatch Log Group for RDS
resource "aws_cloudwatch_log_group" "rds" {
  name              = "/aws/rds/instance/${var.app_name}-postgres/postgresql"
  retention_in_days = 30

  tags = {
    Name = "${var.app_name}-rds-logs"
  }
}

# Database parameter group for pgvector support
resource "aws_db_parameter_group" "main" {
  name   = "${var.app_name}-postgres-params"
  family = "postgres${split(".", var.rds_engine_version)[0]}"

  parameter {
    name  = "shared_preload_libraries"
    value = "vector"
  }

  parameter {
    name  = "max_connections"
    value = "500"
  }

  tags = {
    Name = "${var.app_name}-postgres-params"
  }

  depends_on = [aws_db_instance.main]
}

# Database secrets in AWS Secrets Manager
resource "aws_secretsmanager_secret" "rds_credentials" {
  name                    = "${var.app_name}/rds/credentials"
  recovery_window_in_days = 7
  description             = "RDS PostgreSQL credentials"

  tags = {
    Name = "${var.app_name}-rds-credentials"
  }
}

resource "aws_secretsmanager_secret_version" "rds_credentials" {
  secret_id = aws_secretsmanager_secret.rds_credentials.id
  secret_string = jsonencode({
    username = var.rds_username
    password = var.rds_password
    engine   = "postgres"
    host     = aws_db_instance.main.endpoint
    port     = 5432
    dbname   = var.rds_db_name
  })
}
