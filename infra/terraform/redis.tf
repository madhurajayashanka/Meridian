# ElastiCache Subnet Group
resource "aws_elasticache_subnet_group" "main" {
  name       = "${var.app_name}-cache-subnet-group"
  subnet_ids = aws_subnet.private[*].id

  tags = {
    Name = "${var.app_name}-cache-subnet-group"
  }
}

# ElastiCache Redis Cluster
resource "aws_elasticache_cluster" "main" {
  cluster_id           = "${var.app_name}-redis"
  engine               = "redis"
  node_type            = var.redis_node_type
  num_cache_nodes      = var.redis_num_cache_nodes
  parameter_group_name = "default.redis7"
  engine_version       = var.redis_engine_version
  port                 = 6379
  
  subnet_group_name        = aws_elasticache_subnet_group.main.name
  security_group_ids       = [aws_security_group.redis.id]
  automatic_failover_enabled = true
  multi_az_enabled         = true
  
  # Backup settings
  snapshot_retention_limit = 5
  snapshot_window          = "03:00-05:00"
  
  # Notification
  notification_topic_arn = aws_sns_topic.redis_notifications.arn
  
  # Logging
  log_delivery_configuration {
    destination      = aws_cloudwatch_log_group.redis_slow_log.name
    destination_type = "cloudwatch-logs"
    log_format       = "json"
    log_type         = "slow-log"
  }

  log_delivery_configuration {
    destination      = aws_cloudwatch_log_group.redis_engine_log.name
    destination_type = "cloudwatch-logs"
    log_format       = "json"
    log_type         = "engine-log"
  }
  
  # Maintenance
  maintenance_window = "sun:04:00-sun:05:00"
  
  # At-rest encryption
  at_rest_encryption_enabled = true
  kms_key_id                 = aws_kms_key.redis.arn
  
  # In-transit encryption
  transit_encryption_enabled = true
  auth_token                 = random_password.redis_auth_token.result
  
  tags = {
    Name = "${var.app_name}-redis"
  }

  depends_on = [aws_elasticache_subnet_group.main]
}

# KMS Key for Redis Encryption
resource "aws_kms_key" "redis" {
  description             = "KMS key for Redis encryption"
  deletion_window_in_days = 10
  enable_key_rotation     = true

  tags = {
    Name = "${var.app_name}-redis-key"
  }
}

resource "aws_kms_alias" "redis" {
  name          = "alias/${var.app_name}-redis"
  target_key_id = aws_kms_key.redis.key_id
}

# Redis Auth Token
resource "random_password" "redis_auth_token" {
  length  = 32
  special = true
}

# SNS Topic for Redis Notifications
resource "aws_sns_topic" "redis_notifications" {
  name = "${var.app_name}-redis-notifications"

  tags = {
    Name = "${var.app_name}-redis-notifications"
  }
}

# CloudWatch Log Groups for Redis
resource "aws_cloudwatch_log_group" "redis_slow_log" {
  name              = "/aws/elasticache/${var.app_name}-redis/slow-log"
  retention_in_days = 30

  tags = {
    Name = "${var.app_name}-redis-slow-log"
  }
}

resource "aws_cloudwatch_log_group" "redis_engine_log" {
  name              = "/aws/elasticache/${var.app_name}-redis/engine-log"
  retention_in_days = 7

  tags = {
    Name = "${var.app_name}-redis-engine-log"
  }
}

# Redis Secrets in Secrets Manager
resource "aws_secretsmanager_secret" "redis_credentials" {
  name                    = "${var.app_name}/redis/credentials"
  recovery_window_in_days = 7
  description             = "Redis connection credentials"

  tags = {
    Name = "${var.app_name}-redis-credentials"
  }
}

resource "aws_secretsmanager_secret_version" "redis_credentials" {
  secret_id = aws_secretsmanager_secret.redis_credentials.id
  secret_string = jsonencode({
    host      = aws_elasticache_cluster.main.cache_nodes[0].address
    port      = aws_elasticache_cluster.main.port
    password  = random_password.redis_auth_token.result
    engine    = "redis"
  })
}

# CloudWatch Alarms for Redis
resource "aws_cloudwatch_metric_alarm" "redis_cpu" {
  alarm_name          = "${var.app_name}-redis-cpu"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "EngineCPUUtilization"
  namespace           = "AWS/ElastiCache"
  period              = 300
  statistic           = "Average"
  threshold           = 75
  alarm_description   = "Alert when Redis CPU exceeds 75%"

  dimensions = {
    CacheClusterId = aws_elasticache_cluster.main.cluster_id
  }

  alarm_actions = [aws_sns_topic.redis_notifications.arn]
}

resource "aws_cloudwatch_metric_alarm" "redis_memory" {
  alarm_name          = "${var.app_name}-redis-memory"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "DatabaseMemoryUsagePercentage"
  namespace           = "AWS/ElastiCache"
  period              = 300
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "Alert when Redis memory usage exceeds 80%"

  dimensions = {
    CacheClusterId = aws_elasticache_cluster.main.cluster_id
  }

  alarm_actions = [aws_sns_topic.redis_notifications.arn]
}

resource "aws_cloudwatch_metric_alarm" "redis_evictions" {
  alarm_name          = "${var.app_name}-redis-evictions"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "Evictions"
  namespace           = "AWS/ElastiCache"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  alarm_description   = "Alert when Redis evictions occur"

  dimensions = {
    CacheClusterId = aws_elasticache_cluster.main.cluster_id
  }

  alarm_actions = [aws_sns_topic.redis_notifications.arn]
}
