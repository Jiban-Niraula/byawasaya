# Byawasaya hardening changes (October 2026)

## Included

- Registered platform login MFA enforcement in the active Spring Security chain. Email OTP is the second factor until an account optionally enrolls TOTP; enrolled accounts use TOTP or recovery codes.
- Allowed users to enter administration after successful email OTP without a second password prompt or forced authenticator enrollment.
- Centralized payment prerequisites for order fulfilment and added regression tests for online, advance-COD, and normal COD states.
- Added a manual advance-COD balance receipt path after shipment and prevented delivery until the outstanding balance is recorded.
- Made checkout idempotency require a header at the API boundary, bounded/normalized keys, and excluded provider selection from the order-content hash so a customer can switch provider for the same order.
- Reconciled failed checkout attempts with order payment state only when no active or completed attempt remains.
- Rechecked live payment state in a separate transaction before cancelling pending orders; uncertain provider state fails closed.
- Allowed the idempotency header through CORS and added Vite development API proxy defaults.
- Restored cart/checkout details from session storage after same-origin provider redirects and improved checkout-dialog keyboard focus/Escape behavior.
- Added address input lengths and backend validation for province/district fields.
- Updated migration/MFA documentation and excluded build artifacts, local environment files, and private keys from packaging.

## Verification

- `npm --prefix frontend run build`: passed.
- `npm --prefix frontend run lint`: passed.
- Backend Maven tests were not executable in the editing environment because the Maven wrapper distribution was not present locally and downloading it required unavailable network access. The new `OrderFulfilmentPolicyTest` must be run in a network-enabled environment with Maven available.

## Release caveat

These are code-level fixes and targeted regression tests, not a full production certification. Before launch, run backend tests against PostgreSQL, integration tests for tenant provisioning and payment callbacks, eSewa/Khalti sandbox checks including delayed callbacks, scanner failure/virus acceptance tests, a backup/restore drill, and browser acceptance tests across merchant/customer/platform roles.
