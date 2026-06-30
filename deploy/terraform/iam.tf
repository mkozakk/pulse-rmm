resource "aws_iam_role" "pulse" {
  name = local.name

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_instance_profile" "pulse" {
  name = local.name
  role = aws_iam_role.pulse.name
}

resource "aws_iam_role_policy" "agent_binaries" {
  name = "agent-binaries"
  role = aws_iam_role.pulse.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["s3:ListBucket", "s3:GetBucketLocation"]
        Resource = aws_s3_bucket.agent_binaries.arn
      },
      {
        Effect   = "Allow"
        Action   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
        Resource = "${aws_s3_bucket.agent_binaries.arn}/*"
      },
    ]
  })
}

# certbot --dns-route53 on the host uses this for the dns-01 challenge
resource "aws_iam_role_policy" "certbot" {
  name = "certbot-route53"
  role = aws_iam_role.pulse.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["route53:ListHostedZones", "route53:GetChange"]
        Resource = "*"
      },
      {
        Effect   = "Allow"
        Action   = ["route53:ChangeResourceRecordSets"]
        Resource = aws_route53_zone.pulse.arn
      },
    ]
  })
}
