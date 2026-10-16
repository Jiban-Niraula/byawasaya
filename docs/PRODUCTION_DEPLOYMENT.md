# Production deployment runbook

This repository now includes a production Docker Compose topology. It is deployment scaffolding, not proof that the application has passed a security review or a production recovery drill.

## Before the first deployment

1. Provision a Linux host with Docker Compose, a DNS name, and persistent block storage. Keep the database and Redis ports private; only NGINX should accept public HTTP/HTTPS.
2. Provision PostgreSQL storage, Redis persistence, an HTTPS S3-compatible bucket, TLS SMTP, and Google OAuth credentials. Tenant subdomains resolve as `<slug>.<DOMAIN_NAME>`; configure wildcard DNS and an apex/wildcard certificate. Custom domains are not yet supported.
3. Copy `.env.production.example` to `.env.production`, set `chmod 600 .env.production`, replace every placeholder, and store protected off-host backups of all three encryption keys. Losing the storage key makes documents unreadable; losing the platform MFA key requires resetting authenticator enrollments. To create the first platform administrator, set `SUPERADMIN_BOOTSTRAP_ENABLED=true` and a unique 16+ character `SUPERADMIN_PASSWORD` for the first startup only; then set it to `false`, remove the password, and restart the service. Platform logins require email OTP until an administrator optionally enrolls an authenticator; enrolled users should save the one-time recovery codes.
4. Place the certificate chain and private key at `ops/certs/<DOMAIN_NAME>/fullchain.pem` and `ops/certs/<DOMAIN_NAME>/privkey.pem`. Use a certificate covering the apex and wildcard hostnames, and renew it before expiry.
   Customer session cookies use the `.DOMAIN_NAME` parent domain so customers can view orders across tenant subdomains. Merchant and platform session cookies are host-only, isolating workspace sessions from tenant storefront hosts.
5. Review and pin container image digests for the target host architecture. Configure host firewall rules, SSH access, OS security updates, and disk monitoring.
6. Keep the ClamAV daemon on the private Docker network. It has an unauthenticated, unencrypted TCP protocol; do not publish port 3310. Alert on its health and signature freshness, and test that uploads fail closed during scanner outages.
7. Review applicable Nepal e-commerce, consumer, privacy, tax, and identity-document retention requirements with qualified local counsel before accepting real customer or merchant data.

## Start and inspect

```sh
docker compose --env-file .env.production -f docker-compose.production.yml config --quiet
docker compose --env-file .env.production -f docker-compose.production.yml build
docker compose --env-file .env.production -f docker-compose.production.yml up -d
docker compose --env-file .env.production -f docker-compose.production.yml ps
```

Flyway runs on backend startup. Monitor backend logs and `/actuator/health/readiness` from inside the private Docker network. NGINX redirects HTTP to HTTPS, terminates TLS, and does not proxy actuator endpoints. Database, Redis, Prometheus, and Grafana have no public bind address.

Enable local-only observability when configured:

```sh
docker compose --env-file .env.production -f docker-compose.production.yml --profile observability up -d
```

Prometheus and Grafana bind to loopback on ports 9090 and 3000. Access them through a private VPN or an SSH tunnel; do not expose these ports publicly.

## Backups and restore drills

Run `ops/backup-postgres.sh` from the deployment host after configuring an encrypted backup disk. Schedule it with a host-level systemd timer or cron job. Set `BACKUP_S3_URI` in the backup process environment to copy archives off-host; uploads use S3 server-side encryption, and `BACKUP_S3_KMS_KEY_ID` can select a KMS key. Configure bucket versioning, access controls, and lifecycle retention separately.

Before relying on a backup, run:

```sh
ops/verify-backup.sh /var/backups/byawasaya/byawasaya-<timestamp>.dump
```

This checks the checksum and archive structure only. Regularly restore into a separate PostgreSQL instance, run Flyway/application smoke checks, and record the recovery time and missing-data window. Do not test restoration over the live database.

Back up `.env.production` and the storage encryption key through a separate secret-management process. Never put either in the repository or in a database dump.

## Release gates still outside this runbook

- Configure each tenant's own eSewa/Khalti merchant account and verify sandbox and live initiation, provider callbacks/lookups, amount checks, cancellation, reconciliation, and support procedures. The source integration does not prove live-account readiness; refunds and disputes are not automated.
- Verify customer registration, email verification/resend, email/password sign-in/reset, checkout linking, and order history against real SMTP.
- Verify tenant subdomain routing with the production wildcard DNS/certificate. Implement custom-domain ownership verification and certificate automation before advertising custom URLs.
- Verify platform TOTP enrollment, MFA challenges, replay rejection, recovery codes, account lockout, and audited reset by a second superadmin in a browser.
- Verify ClamAV scanning with clean, infected test-signature, oversized, and scanner-outage uploads; ensure the infected/outage cases create no stored document or object.
- Run PostgreSQL/Redis integration tests, cross-tenant access checks, full browser tests, security review, and successful backup restoration.
- Complete real SMTP/OAuth delivery tests, load and abuse testing, alert routing, incident-response rehearsal, and launch/legal review.
- The manual subscription-invoice workflow is in source. Before charging tenants, configure real platform payment instructions, a process to deliver invoices and reconcile receipts, and a locally reviewed tax-invoice/accounting policy. Automated recurring collection, payment retry and dunning remain unimplemented.
