resource "aws_key_pair" "admin" {
  key_name   = local.name
  public_key = file(pathexpand(var.ssh_public_key_path))
}

resource "aws_instance" "pulse" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.instance_type
  key_name               = aws_key_pair.admin.key_name
  vpc_security_group_ids = [aws_security_group.pulse.id]
  iam_instance_profile   = aws_iam_instance_profile.pulse.name

  root_block_device {
    volume_size = var.root_volume_size
    volume_type = "gp3"
    encrypted   = true
  }

  metadata_options {
    http_tokens = "required"
    # containers sit one network hop behind the host, with 1 they can't reach imds
    http_put_response_hop_limit = 2
  }

  tags = {
    Name = local.name
  }

  lifecycle {
    ignore_changes = [ami]
  }
}

resource "aws_eip" "pulse" {
  instance = aws_instance.pulse.id
  domain   = "vpc"

  tags = {
    Name = local.name
  }
}
