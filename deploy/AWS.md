# AWS deployment (single EC2 host)

The whole stack runs on one EC2 instance with docker compose, the same way `make prod` runs it locally,
plus `compose.aws.yaml` on top.

```
            pulse.example.com (route53 A -> elastic ip)
                         |
   443 webapp/api/auth   |   9090 agent grpc (mTLS passthrough)   3478 + 49152-49200/udp turn
                         v
  +------------------------ EC2 t3.xlarge (ubuntu 24.04, docker) ---------------------------+
  |  nginx (webapp bundle, TLS from certbot) -> api-gateway / agent-hub / keycloak          |
  |  9 spring services, postgres+timescale, redis, rabbitmq, coturn, prometheus/loki/grafana |
  +------------------------------------------------------------------------------------------+
        | instance role                                  ^ secrets
        v                                                |
   S3 bucket (agent binaries)            SSM Parameter Store /pulse-rmm/prod/*
```

| piece | where |
|-------|-------|
| infra (ec2, eip, sg, route53 zone, s3, iam role, ssm secrets) | `deploy/terraform` |
| host setup + deploy (docker, certbot, .env from ssm, compose up) | `deploy/ansible` |
| images | GHCR, built by `.github/workflows/release.yml` after CI is green on main |

## One-time setup

1. Tools: terraform >= 1.10, ansible-core, `pip install boto3`, aws cli logged in.
2. State bucket (once per account):
   ```bash
   aws s3api create-bucket --bucket pulse-rmm-tfstate-<something> --region eu-central-1 \
     --create-bucket-configuration LocationConstraint=eu-central-1
   aws s3api put-bucket-versioning --bucket pulse-rmm-tfstate-<something> \
     --versioning-configuration Status=Enabled
   ```
3. Config:
   ```bash
   cd deploy/terraform
   cp backend.hcl.example backend.hcl            # set the state bucket
   cp terraform.tfvars.example terraform.tfvars  # set domain + your ip
   ```
4. GitHub: add repository variable `PULSE_DOMAIN` (Settings > Secrets and variables > Actions > Variables).
   The webapp image bakes the domain in at build time. Run the "Release - Publish Images" workflow once.
   If the GHCR packages stay private, set `ghcr_pull_token` in tfvars (PAT with `read:packages`).

## Deploy

```bash
make aws-init
make aws-apply                 # prints name_servers and writes deploy/ansible/inventory.ini
```

Set the `name_servers` output as NS records at your domain registrar and wait until
`dig NS pulse.example.com` shows them - certbot uses a dns-01 challenge and fails before that.

```bash
cd deploy/ansible && ansible-galaxy collection install -r requirements.yml && cd ../..
make aws-deploy                # first run: docker, certificate, stack
make aws-deploy tag=<git sha>  # later: roll out a specific build
```

Log in at `https://pulse.example.com` as `admin`. The password is in SSM:

```bash
aws ssm get-parameter --with-decryption --name /pulse-rmm/prod/ADMIN_PASSWORD --query Parameter.Value --output text
```

Agents enrol with `api_url: https://pulse.example.com` and connect to `pulse.example.com:9090` on their own.

## Day to day

- grafana: `ssh -L 3000:localhost:3000 ubuntu@<ip>` then http://localhost:3000
- logs: `ssh ubuntu@<ip>` then `cd /opt/pulse && docker compose -p pulse logs -f api-gateway`
- certificate renews through the certbot systemd timer, the deploy hook reloads nginx

## Known limitations

- one instance, no HA - agent-hub keeps agent streams in memory, so it can't be scaled out yet
- postgres lives on the root EBS volume, there are no automated backups (snapshot the volume or add pg_dump)
- changing `PULSE_DOMAIN` needs a new webapp image (vite inlines it)
- the agent-hub mTLS cert is generated once into a volume; if the domain changes, delete `pulse_agent_hub_tls`
