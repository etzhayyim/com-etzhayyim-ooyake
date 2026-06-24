# ooyake (公) — CLAUDE actor guide

**World government atlas — civic wayfinding map.** Tier-B ·
`did:web:ooyake.etzhayyim.com` · ADR-2606021600 ·
**R0/R1 — `reconcile` + `world_model` (bundled mode) run + unit-tested; live ingest + serve + per-unit DID gated**.

## What this actor IS

The **read-side structural SSoT** of public administration. ooyake enumerates
every government unit — supranational → country → region/subdivision →
prefecture → municipality/ward → ministry (省) → agency (庁) → bureau (局) →
division (課) → section → **窓口 (madoguchi)** — as a recursive kotoba-Datomic
tree, each unit carrying its **住所 / 窓口 / 書式 / 手続き / BPMN**.

It is the map that **danjo** (watches the state), **kanae** (fiscal-flow viz),
**tsumugi** (power-graph), **toritsugi** (citizen procedure concierge) and
**himotoki** (disclosure) all consume for the *who / where / how* of government —
so they stop re-deriving it ad hoc.

```
:gov.unit/* (recursive parent tree)
   ├─ :gov.address/*   住所
   ├─ :gov.window/*    窓口 ── handles ──▶ :gov.procedure/*
   └─ :gov.procedure/* 手続き ── form ──▶ :gov.form/* (→ chigiri template)
                       └─ bpmn ──▶ :gov.bpmn/* (00-contracts/bpmn/.../ooyake/)
                       └─ toritsugi-ref ──▶ com.etzhayyim.toritsugi.procedure (delivery)
   reconcile: :gov.unit/organism ──▶ :organism (engi) for tsumugi's 縁/取 graph
```

Schema: `00-contracts/schemas/gov-atlas-ontology.kotoba.edn`.
Seed (proof-of-model): `registry/gov-units.seed.edn`.

## Posture (the single most important thing)

ooyake is an **OBSERVATIONAL MIRROR + civic wayfinding map**, exactly like
tsumugi ("accountability map, NEVER a target-list") and watatsuna ("resilience
map, NEVER a target-list"). It maps the state **for citizens to find services**.
It is **read-only** and it is **not the government**.

## Do NOT (constitutional invariants — ADR-2606021600 §4)

- **Do not** let the per-unit atlas DID (`did:web:etzhayyim.com:gov:<iso3>:...`)
  claim to BE the government, act as an official channel, or issue/accept
  anything on a government's behalf. It is an etzhayyim **mirror record** of a
  real public body (G3, §2(c) impersonation ban). The DID-doc must declare the
  mirror relation + link the body's `official-url` / `official-did`.
- **Do not** ingest from anything but **public official sources**; respect
  robots.txt / ToS / rate-limits; never access behind-auth; never circumvent
  controls (G4).
- **Do not** write a unit/address/window/form/procedure without `provenance` +
  `last-verified` + `sourcing`; **never** count `:representative` rows as
  coverage; **never** invent a procedure without a cited `legal-basis` (G5).
- **Do not** store any official's **personal/home contact** — public
  switchboard / 窓口 role-contact only, data-minimized (G6).
- **Do not** file, submit, or mutate a government record — that is **toritsugi**
  (gated). **Do not** audit/adjudicate — that is **danjo**. ooyake only
  catalogs (G9).
- **Do not** derive an attack-surface / SPOF / "weak-point" map of the state.
  Civic wayfinding only (G10, Transparent Force §1.12).
- **Do not** rank governments or take a political position — descriptive,
  neutral (G11).
- **Do not** sell the atlas as a data product — public good, donation-only (G8).
- **Do not** use any inference path but Murakumo (G7, ADR-2605215000).

## Boundary with toritsugi / chigiri / danjo / tsumugi

- **toritsugi** = *delivers* a procedure to the citizen (guide/draft/submit/
  track). ooyake = *catalogs* who/where/structure. `:gov.procedure/toritsugi-ref`
  links them; no duplication.
- **chigiri** = *owns* the UPL-bounded fillable form templates. ooyake only
  points via `:gov.form/chigiri-ref`.
- **danjo** = *audits* state open-data. ooyake = *maps* structure; danjo consumes
  the atlas.
- **tsumugi** = *karma* (縁/取) over `:organism` nodes. ooyake = *structure*.
  `:gov.unit/organism` reconciles the SAME unit across both graphs. The join is
  performed by `cells/world_model/` (the cross-actor **world-model reconcile**):
  offline + deterministic, it classifies every **power-bearing** unit as
  confirmed / derived / dangling / proposed against the tsumugi karma graph and
  emits `out/world-model.kotoba.edn` (read-side proposal, G9 — never mutates a
  seed; `mode="live"` is Council+operator gated). Local service surface
  (窓口/ward/division) is **excluded by construction** (G1 power-only, never a
  target-list). Today: 1 confirmed link (`gov.jpn.meti↔org.state.jp.meti`), the
  rest honestly `:proposed`/`:representative` — the world model is mostly
  unreconciled, which is the honest R1 state. `scripts/world_model.py`.

## Legacy `gov*` stubs

`00-contracts/bpmn/com/etzhayyim/gov<ISO3>/` (196 country dirs, ~1,574 BPMN
stubs, legacy `com.etzhayyim.gov*` namespace) + `90-docs/openapi/gov*.openapi.json`
(141 skeletons) are **subsumed** by ooyake's `:gov.*` graph as their kotoba-native
owner. The `com.etzhayyim.gov*` → `com.etzhayyim.ooyake.*` rename is deferred to the
gated Step-8 `etzhayyim-*` cutover (root CLAUDE.md §Do-Not) — **do not rename them
here**.

## Coverage honesty

As of **2026-06-03** the atlas carries the **full G20 as real committed data**
(`registry/gov-units.g20.edn` + promoted national rows): 20/20 members, each with a
country unit + finance ministry, all `:sourcing :authoritative` +
`:verification-status :maintainer-verified` (Wikidata QID web-verified, provenance =
the body's own official URL); G7 finance ministries also carry HQ addresses (L3).
Gates: `scripts/g20_coverage.py` (20/20) + `scripts/check_seed_integrity.py`.

Still gated (NOT done in the registry): **live kotoba ingest** (`KOTOBA_TOKEN` +
node) and **publishing national `:authoritative` rows** to
`/.well-known/gov-units.json` (Council-Lv6+ / bootstrap-attestation gate,
`validate_atlas.py` check #5). The committed registry is the real verified record;
ingest + publish are separate operator/Council steps.

Beyond the G20 the rest is still illustrative (`:representative` /
`:unverified-seed`, e.g. the JP-local ingest, subnational rows) — never counted as
coverage (G5). The offline `reconcile.py` remains a **mechanism demo**, distinct
from the real G20 data.

## Inference

Murakumo-only (LiteLLM 127.0.0.1:4000 / EVO-X2 LAN / per-node Ollama). No vendor
LLM API. See ADR-2605215000.
