data "aws_caller_identity" "current" {}

data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"] # canonical

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"]
  }
}

# default vpc is enough for a single box
data "aws_vpc" "default" {
  default = true
}

locals {
  name       = "pulse-rmm-${var.environment}"
  ssm_prefix = "/pulse-rmm/${var.environment}"
}
