# agent binaries - private, endpoint-service proxies the downloads
resource "aws_s3_bucket" "agent_binaries" {
  bucket = "pulse-rmm-agent-binaries-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket_public_access_block" "agent_binaries" {
  bucket                  = aws_s3_bucket.agent_binaries.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "agent_binaries" {
  bucket = aws_s3_bucket.agent_binaries.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}
