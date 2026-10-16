# Byawasaya architecture

## Runtime components

- React/TypeScript SPA for public storefronts, tenant workspace and platform administration.
- Spring Boot modular monolith for authentication, tenant applications, provisioning, catalog, orders, plans, promotions, subscriptions and audit events.
- PostgreSQL as the system of record and Flyway for versioned global migrations.
- Private ClamAV scanning of identity-document uploads before encrypted S3-compatible storage; scanner access is internal to the application network.
- Redis for cache/session acceleration where configured; PostgreSQL retains the authoritative session registry.
- Private S3-compatible object storage with application-side AES-GCM encryption for citizenship, owner-photo and registration documents. MinIO is used locally.
- Product images and store logos are stored as PostgreSQL `BYTEA` in each provisioned tenant's `store_assets` table.
- Production Docker Compose, TLS proxying, PostgreSQL backups, readiness/liveness probes, and Prometheus/Grafana configuration are included. Operators must still execute restore drills and alert tests.

## Identity and authorization

Platform identities are stored in `platform_users`; tenant owners and staff are stored in `user_accounts`. Tenant membership and staff role are stored in the generated tenant `staffs` table. The browser receives an opaque session credential in an `HttpOnly` cookie. The database stores only a hash of the credential. Accounts can have multiple live sessions; each session stores its browser user-agent and client IP for device review. Account disablement, password changes, MFA reset and tenant suspension revoke all applicable sessions.

Spring Security protects route groups. Frontend guards are for navigation only; server-side filters, method authorization and service checks enforce permissions. Tenant table names are resolved from the trusted provisioning registry and checked against a strict prefix/suffix allowlist. Request-supplied table names are never used.

Every platform login requires a second factor before the session is issued. Users without TOTP enrollment verify an email OTP and can enter the workspace directly; authenticator enrollment is optional. Enrolled accounts use TOTP or a one-use recovery code. Seeds are encrypted with AES-GCM and a dedicated 32-byte key, OTP steps cannot be replayed, and recovery codes are stored as hashes. The key must be backed up separately; a different superadmin can reset another platform user's enrollment.

## Tenant table model

New approved tenants receive a generated prefix such as `t_a13f09bd`. Provisioning creates prefixed `products`, `product_variants`, `categories`, `staffs`, `customers`, `store_assets`, `orders`, `order_items`, `payments`, `payment_events` and `store_settings` tables. Existing ready tenants are upgraded additively before application startup. Fixed platform tables contain identities, applications, plans, subscriptions, promotions, sessions and audit events.

The prefix model is a deliberate requirement and has migration/operations costs. It does not inherently guarantee stronger isolation than separate schemas or shared tables with PostgreSQL RLS; the resolver and provisioning registry are security-sensitive components and must be covered by integration tests.

## Store lifecycle

`DRAFT → SUBMITTED → UNDER_REVIEW → APPROVED → PROVISIONING → TRIAL_ACTIVE` is the normal path. Correction, rejection, suspension and trial expiry are explicit states. A store becomes operational only after document verification, provisioning and trial creation succeed. Public storefront and checkout paths check operational/trial state on the server.

## Payment boundary

Orders support cash on delivery, full online payment, and advance-plus-COD. Tenant owners configure their own eSewa and Khalti merchant accounts; provider secrets are encrypted with an application key separate from document storage. A pending order is not evidence that money was collected. The server validates provider references and amounts against provider-side status/lookup responses before updating payment state. Refund automation, dispute handling, and recurring SaaS subscription collection are not implemented.

## Storefront routing

The implemented public routes are `/store/{slug}` and one-level `<slug>.<base-domain>` subdomains. The base domain is configured with `DOMAIN_NAME`; production DNS and certificates must cover the apex and wildcard. Custom-domain ownership verification, activation, and certificate automation remain unimplemented.

The planned custom-domain extension should store normalized tenant hostnames, require a DNS TXT challenge before activation, and coordinate certificate issuance/renewal with the edge proxy. Resolve a request only from that verified registry or the existing tenant-subdomain resolver.
