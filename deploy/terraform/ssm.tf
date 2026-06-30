locals {
  secrets = toset([
    "POSTGRES_PASSWORD",
    "RABBITMQ_PASSWORD",
    "RABBITMQ_ERLANG_COOKIE",
    "INTERNAL_API_SECRET",
    "SCRIPT_SECRET_KEK",
    "WEBHOOK_SECRET_KEK",
    "CA_ROOT_KEY_SECRET",
    "TURN_SECRET",
    "ADMIN_PASSWORD",
    "KEYCLOAK_ADMIN_PASSWORD",
    "GRAFANA_ADMIN_PASSWORD",
  ])
}

# no special chars - these end up in .env, jdbc urls and shell healthchecks
resource "random_password" "secret" {
  for_each = local.secrets
  length   = 32
  special  = false
}

resource "aws_ssm_parameter" "secret" {
  for_each = local.secrets
  name     = "${local.ssm_prefix}/${each.key}"
  type     = "SecureString"
  value    = random_password.secret[each.key].result
}

resource "aws_ssm_parameter" "ghcr_pull_token" {
  count = var.ghcr_pull_token == "" ? 0 : 1
  name  = "${local.ssm_prefix}/GHCR_PULL_TOKEN"
  type  = "SecureString"
  value = var.ghcr_pull_token
}
