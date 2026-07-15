# cloud-itonami-isic-3314

Open Business Blueprint for **ISIC Rev.5 3314**: repair of electrical
equipment.

This repository designs a forkable OSS business for electrical-
equipment-repair-shop operations coordination: run by a qualified
operator so a community keeps its own operating records instead of
renting a closed SaaS.

ISIC 3314 covers repair of **electrical equipment** specifically --
motors, generators, transformers and switchgear -- distinct from the
more specific/other repair classes 3311 (fabricated metal products),
3312 (machinery and equipment), 3313 (electronic and optical equipment),
3315 (transport equipment, except motor vehicles) and the residual class
3319 (other equipment).

## Scope -- this is a COORDINATION-ONLY actor, not equipment control

This is a safety-relevant domain: electrical equipment repair can
involve stored/residual voltage, arc-flash hazards and incomplete-repair
risk. **This actor does NOT hold repair-equipment/diagnostic-tool
control authority, and it does NOT hold return-to-service or
re-energization sign-off authority.** Both are the licensed repair
technician's exclusive authority, always. The Repair Advisor (LLM) never
issues an equipment/tool-control command and never signs off on
returning repaired equipment to service or re-energizing it; the
independent **Repair Governor** HARD-blocks any proposal that even tries
(un-overridable by any human approval -- see `electrical-equipment-
repair.governor` ns docstring). This actor coordinates *potential*
diagnostic/repair/testing dispatch (a proposed schedule window, a
flagged concern, a supply-order proposal) -- it never directly actuates.

Structurally, EVERY proposal this actor's advisor can produce carries
`:effect :propose`, and the Repair Governor HARD-holds any proposal that
doesn't -- this is a permanent invariant distinguishing this actor from
actors whose sibling ops DO commit real-world effects.

## Core Contract

```text
equipment/work-order record + independent verification
        |
        v
Advisor -> Repair Governor -> proceed (log/schedule/flag/order proposal), hold, or human approval
        |
        v
coordination artifacts (schedule proposal, safety-concern flag,
supply-order proposal) + audit ledger -- NEVER repair-equipment/
diagnostic-tool dispatch, NEVER a return-to-service/re-energization
sign-off
```

No automated advice can propose a schedule the governor refuses, suppress
a safety-concern flag, or slip an equipment-control/return-to-service/
re-energization marker past the governor -- and `:flag-safety-concern`
always needs a human sign-off regardless of how clean the governor's
check comes back (see `Actuation` below).

## Capability layer

Resolves via [`kotoba-lang/industry`](https://github.com/kotoba-lang/industry)
(ISIC `3314`). Required capabilities:

- `:identity`
- `:forms`
- `:audit-ledger`

## Implemented slice (`src/electrical_equipment_repair`)

`blueprint.edn` names the governor `:electrical-equipment-repair-
governor` and is now `:implemented`. This repo implements it end-to-end
-- **Repair Advisor ⊣ Repair Governor** -- following the SAME `.cljc`
actor pattern (langgraph-clj StateGraph, mock-by-default advisor, dual
MemStore/Datomic backend, 0→3 phase rollout) every prior
`cloud-itonami-isic-*` actor in this fleet uses, structured after
[`cloud-itonami-isic-3319`](https://github.com/cloud-itonami/cloud-itonami-isic-3319)
(Repair of Other Equipment -- the closest structural analog: also a
coordination-only repair actor with the same closed op-allowlist and
schedule-op auto-eligibility), narrowed to electrical-equipment-repair-
shop diagnostic/repair/testing coordination as described above.

### Closed op-allowlist (4 ops, all `:effect :propose`)

| Op | Ask | Implementation |
|---|---|---|
| `:log-repair-record` | diagnostic-finding / repair-work-performed / parts-used data logging | Normalizes and commits a patch onto the equipment/work-order's ground-truth fields (`:equipment-verified?`, concern resolution, etc.) and appends an immutable repair-record-log entry. No direct capital/safety risk -- MAY auto-commit at phase 3. |
| `:schedule-repair-operation` | diagnostic/repair/testing scheduling proposal | Drafts a proposed schedule WINDOW (never a repair-equipment/diagnostic-tool control command or a return-to-service/re-energization sign-off). MAY auto-commit at phase 3 when the governor is clean -- see `Actuation` below. |
| `:flag-safety-concern` | surface an electrical-hazard (insulation failure, arc-flash risk) / incomplete-repair concern | Drafts a safety-concern flag; ALWAYS escalates to a human, unconditionally. Once approved, `electrical-equipment-repair.notify` sends the notice (mail + phone, mock only -- see `Actuation`) to the equipment/work-order's repair-technician/shop-safety-officer contact roster. |
| `:order-supplies` | replacement-parts procurement proposal | Drafts a supply-order proposal. Escalates above a cost threshold or below the confidence floor; may auto-commit at phase 3 otherwise. |

**Legal basis is data, not code** -- `src/electrical_equipment_repair/facts.cljc`'s
`catalog` is the per-jurisdiction EDN source-of-truth the governor checks
every `:schedule-repair-operation` proposal against (JPN/USA/DEU seeded,
the same honest-coverage convention `installation.facts`/`demolition.
facts`/`construction.facts`/`other-equipment-repair.facts` use; DEU
stands in for the EU):

| Jurisdiction | Pre-repair de-energization / re-energization legal basis |
|---|---|
| 🇯🇵 Japan | 労働安全衛生規則（昭和47年労働省令第32号）第339条（停電作業を行なう場合の措置 -- 電気工事の作業のため電路を開路するときの施錠・通電禁止表示・監視人配置、残留電荷の放電、検電・接地の義務。第2項: 通電（再通電）前の感電危険が無いこと及び短絡接地器具取り外しの確認義務） -- [e-Gov](https://laws.e-gov.go.jp/law/347M50002000032) |
| 🇺🇸 USA | OSHA 29 CFR 1910.333 (Selection and use of work practices -- Electrical safety-related work practices: live parts must be deenergized before work, and a qualified person must verify the deenergized condition with test equipment before work begins) -- [osha.gov](https://www.osha.gov/laws-regs/regulations/standardnumber/1910/1910.333) |
| 🇪🇺 EU (DEU proxy) | DGUV Vorschrift 3 "Elektrische Anlagen und Betriebsmittel" §3 (Prüfungen -- electrical installations/equipment must be inspected before first commissioning AND after any repair or change, before recommissioning, by a qualified electrician), grounded in Directive 2009/104/EC (minimum safety and health requirements for the use of work equipment by workers) -- [publikationen.dguv.de](https://publikationen.dguv.de/widgets/pdf/download/article/1052) / [EUR-Lex](https://eur-lex.europa.eu/eli/dir/2009/104/oj/eng) |

All three seeded jurisdictions are honestly `:qualitative` here -- every
source is a PROCEDURAL requirement (de-energize before work, verify with
test equipment, use qualified personnel, and -- JPN/DEU explicitly --
confirm safety before re-energizing/recommissioning) with no fixed
numeric advance-notice-days count this actor could independently verify.
`electrical-equipment-repair.facts/notification-lead-insufficient?`
reports `:qualitative` for every covered jurisdiction rather than
fabricating a number. See `electrical-equipment-repair.facts` ns
docstring for the full honesty discipline.

**Governor -- six HARD checks, ALL un-overridable by human approval:**
unknown op (outside the closed 4-op allowlist), `:effect` not
`:propose`, forbidden action class (repair-equipment/diagnostic-tool-
control / direct-actuation / return-to-service-sign-off / re-
energization-sign-off markers), equipment/work-order not independently
verified/registered, legal-basis missing, unresolved safety concern. See
`electrical-equipment-repair.governor` ns docstring for the full
enumeration, rationale and real-law citations behind each.

## Actuation

This actor performs **no real-world actuation** -- every committed
record carries `:effect :propose` (see `electrical-equipment-repair.
governor` ns docstring). `:flag-safety-concern` NEVER auto-commits at any
phase -- it always needs a human sign-off, even when the governor is
completely clean (`electrical-equipment-repair.phase` ns docstring
'Actuation' section, `electrical-equipment-repair.governor`'s
`high-stakes` set).

**Like `cloud-itonami-isic-3319`'s `:schedule-repair-operation` (and
UNLIKE `cloud-itonami-isic-3320`'s `:schedule-installation-operation`,
which always escalates because it coordinates potential heavy-lift/
rigging-equipment dispatch), this actor's `:schedule-repair-operation`
MAY auto-commit at phase 3** when the governor is clean (equipment
independently verified, legal-basis on file, no unresolved safety
concern). Electrical equipment repair (motors, generators, transformers,
switchgear) carries a genuinely higher intrinsic electrical hazard
(arc-flash, stored/residual voltage, insulation breakdown) than 3319's
residual scope -- but the schedule op itself is still only ever a
proposed diagnostic/repair/testing WINDOW, never a live-work
authorization, and this actor's own HARD checks (equipment-verification,
legal-basis-on-file, no-unresolved-concern) PLUS its forbidden-action-
class block on any `:re-energization-sign-off?` marker already gate the
real hazard surface independently of phase. `:log-repair-record` (pure
data logging) and `:order-supplies` BELOW the cost threshold
(`electrical-equipment-repair.governor/supply-order-cost-threshold-usd`)
also MAY auto-commit at phase 3 when the governor is clean.

This build also deliberately ships **NO JVM-only interop anywhere in
`src/`** -- `electrical-equipment-repair.notify` ships only the
deterministic mock `Notifier` (no real Resend/Twilio transport), per
this workspace's cljs-first `.cljc` runtime-priority rule. A real
transport can be added later behind the same protocol via a portable
HTTP client without changing this actor's shape.

```bash
clojure -M:dev:run    # demo: full coordination episode + every HARD hold
clojure -M:dev:test   # test suite
clojure -M:lint       # clj-kondo, errors fail
```

## License

AGPL-3.0-or-later.
