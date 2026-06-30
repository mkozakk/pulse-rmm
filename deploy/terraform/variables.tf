variable "aws_region" {
  type    = string
  default = "eu-central-1"
}

variable "environment" {
  type    = string
  default = "prod"
}

variable "domain" {
  description = "Public domain for the whole stack, e.g. pulse.example.com. Terraform creates a hosted zone for it."
  type        = string
}

variable "instance_type" {
  description = "9 JVMs + keycloak + postgres need ~12 GB, so 16 GB is the safe minimum"
  type        = string
  default     = "t3.xlarge"
}

variable "root_volume_size" {
  type    = number
  default = 50
}

variable "ssh_public_key_path" {
  type    = string
  default = "~/.ssh/id_ed25519.pub"
}

variable "admin_cidr" {
  description = "Who can ssh into the box, e.g. your home ip with /32"
  type        = string
}

variable "ghcr_pull_token" {
  description = "Only needed when the ghcr packages are private (read:packages PAT)"
  type        = string
  default     = ""
  sensitive   = true
}
