resource "aws_route53_zone" "pulse" {
  name = var.domain
}

resource "aws_route53_record" "pulse" {
  zone_id = aws_route53_zone.pulse.zone_id
  name    = var.domain
  type    = "A"
  ttl     = 300
  records = [aws_eip.pulse.public_ip]
}
