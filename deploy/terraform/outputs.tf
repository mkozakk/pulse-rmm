output "public_ip" {
  value = aws_eip.pulse.public_ip
}

output "name_servers" {
  description = "Set these as NS records at your domain registrar"
  value       = aws_route53_zone.pulse.name_servers
}

output "agent_binaries_bucket" {
  value = aws_s3_bucket.agent_binaries.bucket
}

output "ssm_prefix" {
  value = local.ssm_prefix
}

output "ssh" {
  value = "ssh ubuntu@${aws_eip.pulse.public_ip}"
}

# ansible reads everything it needs from here
resource "local_file" "ansible_inventory" {
  filename        = "${path.module}/../ansible/inventory.ini"
  file_permission = "0644"
  content         = <<-EOT
    [pulse]
    ${aws_eip.pulse.public_ip} ansible_user=ubuntu

    [pulse:vars]
    pulse_domain=${var.domain}
    aws_region=${var.aws_region}
    s3_bucket=${aws_s3_bucket.agent_binaries.bucket}
    ssm_prefix=${local.ssm_prefix}
    turn_external_ip=${aws_eip.pulse.public_ip}
  EOT
}
