resource "aws_security_group" "pulse" {
  name        = local.name
  description = "pulse rmm single host"
  vpc_id      = data.aws_vpc.default.id
}

resource "aws_vpc_security_group_ingress_rule" "ssh" {
  security_group_id = aws_security_group.pulse.id
  cidr_ipv4         = var.admin_cidr
  ip_protocol       = "tcp"
  from_port         = 22
  to_port           = 22
}

resource "aws_vpc_security_group_ingress_rule" "http" {
  security_group_id = aws_security_group.pulse.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
}

resource "aws_vpc_security_group_ingress_rule" "https" {
  security_group_id = aws_security_group.pulse.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

# agents connect here with mTLS
resource "aws_vpc_security_group_ingress_rule" "agent_grpc" {
  security_group_id = aws_security_group.pulse.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 9090
  to_port           = 9090
}

resource "aws_vpc_security_group_ingress_rule" "turn_tcp" {
  security_group_id = aws_security_group.pulse.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 3478
  to_port           = 3478
}

resource "aws_vpc_security_group_ingress_rule" "turn_udp" {
  security_group_id = aws_security_group.pulse.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "udp"
  from_port         = 3478
  to_port           = 3478
}

# has to match --min-port/--max-port of coturn
resource "aws_vpc_security_group_ingress_rule" "turn_relay" {
  security_group_id = aws_security_group.pulse.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "udp"
  from_port         = 49152
  to_port           = 49200
}

resource "aws_vpc_security_group_egress_rule" "all" {
  security_group_id = aws_security_group.pulse.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}
