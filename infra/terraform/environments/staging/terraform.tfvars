environment           = "staging"
aws_region            = "us-east-1"
rds_instance_class    = "db.t4g.medium"
rds_allocated_storage = 50
rds_engine_version    = "16"
rds_ca_identifier     = "rds-ca-rsa2048-g1"
redis_node_type       = "cache.t4g.small"
redis_num_cache_nodes = 1
eks_desired_size      = 2
eks_min_size          = 2
eks_max_size          = 6
eks_cluster_version   = "1.30"
eks_instance_type     = "t3.medium"
enable_nat_gateway    = true
rds_db_name           = "meridiandb"
rds_username          = "meridian"
# rds_password — set via TF_VAR_rds_password env var
