# Byawasaya production acceptance checklist

This checklist is a release gate. A source implementation does not count as accepted until the behavior below is verified in an isolated production-like environment.

## Accounts, roles, and tenant isolation

- [ ] Platform, tenant, staff, and customer roles cannot cross their authorized routes.
- [ ] Sessions are HttpOnly, Secure in production, CSRF-protected, allow multiple devices per account, record user-agent and client IP, and are revoked after account or tenant suspension.
- [ ] Every platform role is forced through TOTP setup; login challenges reject replayed codes, recovery codes work once, lockout is enforced, and another superadmin can perform an audited reset.
- [ ] Email/password customer registration, verification/resend, sign-in/password reset, and cross-store order history work end to end.
- [ ] Staff permissions and plan limits are enforced by backend requests, not only hidden UI controls.
- [ ] A tenant cannot read or change another tenant's products, orders, customers, assets, payment credentials, or settings.
- [ ] Two concurrent requests using the same checkout idempotency key create one order; a changed payload conflicts.
- [ ] Repeated login, registration, verification, and recovery requests respect rate limits without exposing account existence.

## Onboarding and data protection

- [ ] Required owner/business fields and documents are validated before review and approval.
- [ ] Every upload is malware-scanned before storage; infected files are rejected, and scanner outages fail closed without persisting the upload.
- [ ] Private documents remain encrypted and inaccessible through public storefront endpoints.
- [ ] Internal reviewer notes never appear in applicant responses.
- [ ] Failed tenant provisioning leaves the store non-operational and can be retried safely.
- [ ] Existing tenant table upgrades are exercised from each supported schema version.
- [ ] Data retention, deletion, export, and key recovery procedures are documented and approved.
- [ ] ClamAV health, signature freshness, resource use, and private network exposure are monitored in the target deployment.

## Store and commerce

- [ ] Public store catalogue, retail/wholesale prices, minimum quantities, cart, delivery details, and order status transitions work on desktop and mobile.
- [ ] Products with variants require a valid active option at checkout; the server applies variant prices, and order history preserves the selected option and price after catalogue edits.
- [ ] The backend recalculates prices, delivery charges, minimums, product payment rules, and advance amounts.
- [ ] COD is recorded only after delivery; online payment state cannot be manually marked as received.
- [ ] Store URL resolution works through `/store/{slug}` and `<slug>.<base-domain>`; verified custom-domain routing remains a release blocker until implemented and tested.
- [ ] Shipping charges match the actual launch policy. Current implementation supports one flat fee per store, not province/district rate tables.

## Payments and SaaS billing

- [ ] Each tenant enters credentials for its own eSewa and/or Khalti merchant account; secrets never return from the settings API.
- [ ] Sandbox and live payment initiation use the correct provider endpoints.
- [ ] Successful payment is recorded only after provider-side status verification, matching merchant reference and amount.
- [ ] Duplicate callbacks, forged callbacks, provider timeouts, user cancellation, delayed confirmation, and concurrent checkout are exercised.
- [ ] Refund, partial-refund, and payment-dispute handling is defined before real-money use.
- [ ] Monthly and annual Byawasaya subscription invoices show immutable price, tenant, service-period, due-date, and first-invoice promotion snapshots to the correct tenant owner, including after trial expiry.
- [ ] Only a superadmin can issue or void an invoice and record a verified full payment; duplicate payments are rejected, every change is audited, and direct subscription edits cannot mark a plan paid.
- [ ] Platform payment instructions, manual receipt verification/reconciliation, invoice delivery, and tax treatment are configured and reviewed before charging tenants.
- [ ] Automated recurring collection, payment retries, refunds, partial refunds, and dunning are explicitly treated as unimplemented until separately built and accepted.

## Deployment and operations

- [ ] `cd frontend && npm ci && npm run lint && npm run build` passes.
- [ ] `cd backend && ./mvnw test` passes.
- [ ] PostgreSQL migrations and startup schema upgrades pass on a disposable production-like database.
- [ ] Production secrets, SMTP, OAuth, S3, HTTPS, and DNS are configured and verified.
- [ ] Monitoring alerts reach an owner; PostgreSQL and Redis are not publicly exposed.
- [ ] A checksum-verified backup is restored to a separate database and the recovery time/data loss are recorded.
- [ ] Security review, penetration testing, load testing, incident response, and local legal review are complete.
