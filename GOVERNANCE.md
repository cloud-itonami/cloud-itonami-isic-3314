# Governance

`cloud-itonami-isic-3314` is an OSS open-business blueprint for
electrical-equipment-repair-shop operations coordination -- coordination-
only, never repair-equipment/diagnostic-tool control or return-to-
service/re-energization sign-off.

## Maintainers

Maintainers may merge changes that preserve these invariants:
- this actor never holds repair-equipment/diagnostic-tool-control
  authority.
- this actor never holds return-to-service or re-energization sign-off
  authority -- that remains the licensed repair technician's exclusively.
- every proposal this actor's advisor produces carries `:effect
  :propose`, and the Repair Governor remains independent of the advisor.
- hard policy violations (unknown op, non-`:propose` effect, forbidden
  action class, unverified equipment/work-order, missing legal basis,
  unresolved safety concern) cannot be overridden by human approval.
- `:flag-safety-concern` always requires human sign-off, at every phase,
  unconditionally.
- every proposal, sign-off, log entry and notification path is
  auditable.
- sensitive operating and personal data stays outside Git.
- no JVM-only interop is added to `src/` (this build's cljs-first
  `.cljc` runtime-priority mandate) -- a real notification transport, if
  ever added, must go behind `electrical-equipment-repair.notify/
  Notifier` via a portable (cljs/nbb) HTTP client, not `java.net.http`.

## Decision Records

Architecture decisions should be documented (an ADR or equivalent) when
changing the trust model, storage contract, closed op-allowlist, business
model, operator certification or license -- including any change to which
ops are auto-eligible at phase 3 (see `electrical-equipment-repair.
phase` ns docstring for why `:schedule-repair-operation`'s auto-
eligibility is a deliberate design choice for this specific ISIC class).

## Operator Governance

Anyone may fork and operate independently. itonami.cloud certification is
a separate trust mark and should require security, safety, audit and
data-flow review.

Certified operators can lose certification for:
- bypassing the Repair Governor's hard checks or the closed op-allowlist
- attempting to extend this actor's authority into repair-equipment/
  diagnostic-tool control or return-to-service/re-energization sign-off
- mishandling sensitive data
- misrepresenting certification status
- failing to respond to security or safety incidents
