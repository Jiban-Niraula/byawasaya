# Byawasaya implementation and release status

Byawasaya is being developed as a commercial, Nepal-focused multi-tenant commerce SaaS. This document describes source implementation; it does not certify a deployment or replace live payment, security, compliance, or recovery checks.

## Implemented in source

- Separate platform and tenant workspaces, server-managed opaque sessions, CSRF protection, role checks, email verification, password recovery for platform/tenant users, Google OAuth account linking, staff invitations, and audit events.
- MFA for every platform login: email OTP is used until optional password-confirmed TOTP enrollment; enrolled accounts use replay-protected TOTP or one-use hashed recovery codes. Authenticator seeds are encrypted, login attempts are rate-limited, and reset by another superadmin is audited.
- Tenant application review, private encrypted document storage, generated physical table prefixes, trial/subscription administration, server-side plan limits, and a legacy PostgreSQL migration path.
- Uploaded identity documents are sent to a private ClamAV service before storage; uploads fail closed if the scanner is unavailable, reports a threat, or detects a scan limit. Encrypted documents are rejected because their contents cannot be inspected. The socket client is bounded by upload size, timeout, and concurrency limits.
- Store catalogues with optional sellable variants, retail and wholesale pricing, product payment policies, storefront checkout, server-calculated totals, idempotent order creation, order lifecycle controls, and delivery configuration through a flat store-level fee. Product categories remain merchant-entered text rather than a managed taxonomy.
- Customer email/password registration, email verification and resend, sign-in, password reset, verified-account order linking, and a private cross-store order history.
- Tenant-configurable eSewa and Khalti merchant credentials encrypted at rest; redirect initiation, signed eSewa response validation, provider-side status/lookup checks, amount matching, server-side payment state updates, and a throttled status-recheck path. Each tenant's own merchant account receives its own payments.
- Manual Byawasaya subscription billing: monthly/annual invoices use immutable plan and tenant snapshots, invoice periods and due dates; owners can see invoices after trial expiry; administrators can record a verified full payment or void an open invoice with an audit trail. Payment records are append-only; payment activates eligible subscriptions, while cancelled subscriptions and manually suspended businesses remain inactive. Tenant store payments remain separate and settle to each tenant's own eSewa/Khalti account.
- Production Dockerfiles and Compose topology, required-secret validation, TLS reverse-proxy configuration, private service networking, health probes, Prometheus/Grafana wiring, PostgreSQL backup scripts, and a deployment runbook.

## Release blockers and external verification

1. Tenant subdomain resolution is implemented for one-level `<slug>.<base-domain>` hosts, alongside `/store/{slug}`. Configure the production base domain, wildcard DNS, and apex/wildcard TLS certificate. Custom-domain ownership verification, activation, and certificate automation are not implemented.
2. Flyway applied all ten migrations and the backend started against disposable PostgreSQL. Platform MFA enrollment/recovery/challenge routes were exercised against that database. The manual invoice/payment API was also smoke-tested against PostgreSQL for issue, owner visibility, verified payment, duplicate rejection, void/reissue and role isolation. Still exercise tenant provisioning upgrades, checkout idempotency, session revocation, and broader cross-tenant access with repeatable database-backed integration checks.
3. Configure each merchant's real eSewa/Khalti account and verify sandbox and live checkout, callbacks, retries, cancellations, reconciliation, and support procedures. Refund automation and provider dispute handling are not implemented.
4. SaaS invoices are issued manually and payment is recorded by a platform superadmin after independently verifying receipt. Configure and communicate actual payment instructions, accounting/reconciliation steps, email or delivery process, and a Nepal-compliant tax-invoice policy before charging tenants. Automated recurring collection, payment retries/dunning, refunds and a platform payment gateway are not implemented.
5. SMTP delivery and email reputation must be verified before launch.
6. Complete an end-to-end browser pass that exercises MFA enrollment, recovery codes, lockout and admin reset, and an upload pass against ClamAV with clean, EICAR-test, unavailable-scanner, and oversized-file cases. Also complete dependency/security review, penetration testing, and load/abuse testing.
7. Perform and record an off-host backup restore drill, encryption-key recovery/rotation plan, production alert routing, and incident-response rehearsal.
8. Obtain local legal review for Nepal e-commerce, privacy, tax, consumer-protection, identity-document retention, and payment-provider terms before accepting real customer or merchant data.
9. Variant catalogue support is implemented in the API, tenant and shared-table paths, storefront selector and order snapshots. Complete its PostgreSQL upgrade and checkout integration verification before release. Region-specific shipping rates are not implemented; delivery currently uses one flat store fee. Inventory quantity tracking remains intentionally out of scope.
10. Tenant document upload requires the official ClamAV service configured in Compose. Production binds it only to the private Docker network; local development binds TCP 3310 to loopback only. Keep the daemon and signature database patched, alert on scanner health, and never publish its unauthenticated TCP socket.

## Custom-domain follow-on

Keep Byawasaya subdomains as the default. Add custom domains with a tenant-owned hostname registry, one-time DNS TXT ownership challenges, an explicit verified/active lifecycle, and automated HTTPS issuance/renewal at the edge. Reject a hostname until both ownership and TLS routing are verified; never resolve arbitrary `Host` values directly to a tenant.

## Known isolation design

Tenant-prefixed physical tables are retained to match the agreed architecture. Their identifiers are resolved from the trusted provisioning registry and validated before use. Prefixes do not by themselves provide the same isolation guarantee as separate databases; integration tests and a security review remain release blockers.

## MFA key operations

`APP_PLATFORM_MFA_ENCRYPTION_KEY_BASE64` must be a separately generated 32-byte key and backed up outside the database. Changing or losing it makes existing authenticator seeds unusable. If an account still has an active superadmin, that administrator can reset another platform user's MFA; recovery for a sole locked-out superadmin needs a reviewed break-glass procedure before launch.
