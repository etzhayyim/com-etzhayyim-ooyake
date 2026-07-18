# ooyake 公 — World Government Atlas

> A member-authorized civic procedure support agent over every government on Earth — **not** the government.

`did:web:ooyake.etzhayyim.com` · Tier-B · ADR-2606021600 · **R1 — real verified data, every tier**

ooyake is the kotoba-Datomic-native **structural atlas** of public administration:
supranational → country → region → prefecture → municipality/ward → ministry (省)
→ agency (庁) → bureau (局) → division (課) → section → **窓口**, each unit carrying
its **住所 (address) · 窓口 (service window) · 書式 (form) · 手続き (procedure) ·
langgraph/Pregel EDN procedure graph**. BPMN is optional visual/legacy interop, not
the primary execution model.

It is the single member-support SSoT that the other government-facing actors consume:

| Actor | Uses ooyake for |
|---|---|
| **toritsugi** 取次 | procedure delivery/handoff for guide/draft/submit/track flows |
| **danjo** 弾正 | the canonical unit list to cross-reference open-data against |
| **kanae** 鼎 | the units to render fiscal flows over |
| **tsumugi** 紡ぎ | reconciling a unit to its `:organism` 縁/取 karma node |
| **himotoki** 繙き | which authority + 窓口 to file a 開示請求 / FOIA against |

## Posture

An **etzhayyim member-support agent + civic operations map**. It helps members
understand, prepare, track, and hand off administrative procedures and public-body
activity, while staying explicit that it is not the government.

- Per-unit atlas DIDs (`did:web:etzhayyim.com:gov:<iso3>:...`) are etzhayyim
  **mirror records** of real public bodies. They never claim to BE the
  government, never act as an official channel (G3, §2(c)).
- **Member-authorized support**: ooyake may guide, draft, prepare, track, and
  hand off administrative work for an etzhayyim member. External submission,
  payment, signature, or government-record mutation requires explicit member
  consent, applicable legal/licensed boundary checks, and an audit trail (G9).
- Never an attack-surface map of the state (G10).

## Coverage (real data, 2026-06-04)

**~6,535 `:gov.unit` rows across ~190 jurisdictions, all `:authoritative` /
`:maintainer-verified`** with an independently-verified Wikidata QID and (where
recorded) the body's own official URL. Spans every tier:

| Tier / branch | What | ~count |
|---|---|---|
| supranational | UN system + major IGOs (intergovernmental) | 96 |
| country | current UN member states | 192 |
| subnational | first-level admin divisions (states/provinces/regions) | 3,599 |
| legislative | national legislatures | 186 |
| judicial | supreme/highest courts | 144 |
| executive | 18 ministry types (finance/foreign/defense/interior/health/justice/education/…) | ~1,850 |
| independent | central banks (158) + audit / ombudsman / electoral / NHRI / anti-corruption / data-protection / competition / financial-regulator / statistics | ~420 |

Plus **~5,693 `:gov.address`** (4,521 with precise lat/lon: national-body HQs +
subnational seats + national capitals) → a derivable world-government **GeoJSON**.

Honest gaps: a few categories are Wikidata-typing-thin (water/industry/competition);
microstates carry fewer bodies; some bodies are sub-national mis-typings retained by
the one-per-country dedup. See [`MATURITY.md`](MATURITY.md) (per-iteration record).

## Layout

```
20-actors/ooyake/
├── manifest.edn                 # DID manifest + cells + gates + non-goals
├── CLAUDE.md / README.md / MATURITY.md
├── registry/                    # ~30 gov-units*.edn (the canonical EDN data)
│   ├── gov-units.seed.edn / gov-units.jp-central.seed.edn   # JP backbone
│   ├── gov-units.g20*.edn       # G20 countries + finance ministries + central banks
│   ├── gov-units.world-*.edn    # world countries / ministries / legislatures / courts / central banks
│   ├── gov-units.oversight-*.edn# audit/ombudsman/electoral/NHRI/anticorruption/… /statistics/revenue
│   ├── gov-units.adm1-*.edn      # first-level subdivisions (5 continent files) + adm1-coords
│   ├── gov-units.intergov.edn / gov-units.capitals.edn / gov-units.hq-locations*.edn
│   └── authority-reference.edn  # reconcile-demo fixture
├── scripts/                     # check_seed_integrity · atlas_summary · coverage_matrix
│   │                            #   · world_coverage · g20_coverage · reconcile · export_geojson
├── cells/reconcile/             # ReconcileCell + tests (incl. integrity-guard self-tests)
├── cells/world_model/           # WorldModelCell — ooyake↔tsumugi cross-actor reconcile + tests
├── deploy/run_tests.sh          # offline gate runner (integrity + coverage + reconcile + …)
└── viz/
    ├── gov-atlas.geojson         # 4,521-feature world-government map (generated)
    └── gov-atlas-map.htm         # self-contained browser viewer (no CDN/tiles)

00-contracts/schemas/gov-atlas-ontology.kotoba.edn   # :gov.* ontology (level/branch enums)
00-contracts/lexicons/com/etzhayyim/ooyake/*.json    # XRPC lexicons (member support)
90-docs/adr/2606021600-ooyake-world-government-atlas-tier-b-actor-r0.md
```

## Tooling (all offline, `bash deploy/run_tests.sh`)

- `scripts/check_seed_integrity.py` — guards QID uniqueness/format, the
  level/branch/sourcing enums, G5 provenance, and address→unit + parent refs.
- `scripts/atlas_summary.py` — by level / branch / sourcing / jurisdiction.
- `scripts/coverage_matrix.py` — per-country presence across 35 functional categories.
- `bb coverage` / `scripts/coverage_matrix.clj` — the kotoba/CLJC coverage matrix:
  suffix + explicit COFOG + conservative multi-portfolio name signals.
- `scripts/export_geojson.py` — derive `viz/gov-atlas.geojson` from the registry.
- `scripts/world_model.py` — cross-actor **world-model reconcile**: join the
  structural atlas (`:gov.unit/*`) to tsumugi's karma graph (`:organism/*`) over the
  shared `:gov.unit/organism` id-space; `--edn out/` writes the proposed world-model
  artifact. Offline, member-support proposal (G9) — see "World model" below.
- open `viz/gov-atlas-map.htm` in a browser to explore the map.

## World model (cross-actor reconcile)

ooyake is **structure**; tsumugi (`:organism/* + :en/*`) is **karma** (縁/取). The
two are joined per-entity by the `:gov.unit/organism` ref — the same public body
seen from both graphs. `cells/world_model/` performs that reconcile OFFLINE and
deterministically, classifying every **power-bearing** unit (country / supranational
/ cabinet / ministry / agency / bureau / legislature / court — local 窓口/ward/
division are **excluded by construction**, civic support never a target-list) as:

- **confirmed** — explicit `:gov.unit/organism` whose target organism exists
  (today: `gov.jpn.meti → org.state.jp.meti`);
- **derived** — derived organism id (`gov.X → org.gov.X`) already in the karma graph;
- **dangling** — explicit link with a MISSING target (G5 flag);
- **proposed** — power-bearing, no counterpart → a `:latent` / `:representative`
  organism stub + proposed link written to `out/world-model.kotoba.edn`.

It also flags **orphan** governmental organisms (in the karma graph, absent from the
atlas) and counts non-gov organisms (corps/roles → kabuto/kanjo/tsumugi, correctly
out of atlas scope). It is a **member-support proposal**: it never mutates a committed
seed — applying a proposed link is a separate operator-gated step (`mode="live"`
raises). Run: `python3 scripts/world_model.py --edn out/`.

Beyond matched nodes, the world model resolves and persists the actual cross-graph
join:

- **Government stewardship paths** — reconciled gov-unit → its organism →
  `:tends`/`:custodies` 縁 → entity. The report's `government_stewardship` view + the
  `:government-stewardship` block in the artifact (e.g. `gov.eu --:tends--> Apple`,
  `gov.usa.sec --:tends--> NVIDIA`). 9 reconciled gov nodes wire **20 paths** today.
- **Bidirectional query** — `regulators_of(entity)` (reverse: who governs X?) and
  `stewarded_entities_of(gov_unit)` (forward). Consumed by tsumugi/danjo/kanae via
  `deploy/consumers_example.py::world_model_regulators`; CLI:
  `scripts/world_model.py --entity org.corp.us.apple` → `{gov.eu, gov.usa.sec}`.
- **kotoba persistence** — `deploy/ingest_world_model.py` projects the reconciled,
  factual model (NOT the proposals) into the canonical named graph **`world-model-v1`**
  (`world.gov` entities with `world/organism` + `world/stewards` relations). Dry-run
  by default; live ingest operator-gated (`KOTOBA_TOKEN`); never auto-seals.
- **Drift-lock** — `cells/world_model/test_consistency.py` binds the registry links,
  tsumugi seed, coverage gate, manifest, ingest graph name, and runner to one SSoT.

The 9 confirmed links today (METI, FSA, BOJ, SEC, Fed, EU, UK CMA, US DOJ, JFTC) are
all publicly-documented regulator→entity ties; the rest of the atlas is honestly
`:proposed`/`:representative`. All offline + gated; `bash deploy/run_tests.sh`.

## COFOG Ministry Layer

ooyake now treats **COFOG** (Classification of the Functions of Government) as the
government-function numbering layer for ministry-level rows.

- `:gov.unit/cofog` is the canonical per-unit government-function code vector.
- `src/ooyake/cofog.cljc` holds the pure CLJC taxonomy for COFOG divisions/classes
  and the conservative `ministry-kind -> COFOG` mapping.
- `cofog/ministry-unit` constructs ministry rows with COFOG attached while leaving
  provenance, sourcing, and verification status explicit.
- `cofog/classify-unit` respects existing explicit `:gov.unit/cofog` first, then
  infers only from `:gov.unit/ministry-kind` or conservative name/id signals.
- `:cofog_classify` is a support-metadata Murakumo cell that emits
  `com.etzhayyim.ooyake.cofog_classification` records. It is not an official
  budget/expenditure claim and never acts as a government channel.

This keeps the atlas usable at both levels:

| Level | Example | COFOG |
|---|---|---|
| government unit | `gov.usa`, `gov.jpn` | usually `01` for general public services |
| ministry | `gov.jpn.mof`, `gov.usa.defense` | `01.1`, `02`, `07`, `09`, ... |
| procedure | `:gov.procedure/*` | routed via owner unit; fiscal/functional rollups use the owner unit's COFOG |

Fiscal actors such as `kanae` can roll budget items or programs up through this
classification, while citizen-facing actors such as `toritsugi` still route through
the concrete `:gov.procedure/owner-unit` and `:gov.window/*`.

## Coverage Layer

`src/ooyake/coverage.cljc` is the kotoba-native coverage model for the atlas.

- It counts stable national-body suffixes, explicit `:gov.unit/cofog`, and conservative
  multi-portfolio ministry names.
- It does not fabricate units or mark `:representative` rows as complete.
- `:coverage_plan` emits `com.etzhayyim.ooyake.coverage_gap` records for missing
  country/category pairs.
- This fixes undercounting for merged ministries such as "Trade and Industry" or
  "Environment and Water", where one real body carries multiple functions.

## Procedure Graph Layer

ooyake treats procedure flow as **EDN data first**:

- `src/ooyake/procedure_graph.cljc` projects each `:gov.procedure/*` row into a
  langgraph-style `:state-graph` plus a Pregel/BSP superstep plan.
- `:procedure_graph` is the Murakumo member-support cell that emits
  `com.etzhayyim.ooyake.procedure_graph` records.
- `:procedure.graph/primary` is always `:langgraph-pregel`.
- `:procedure.graph/bpmn-ref`, when present, is `:visual-interop-only` legacy
  metadata. It is not the execution authority.
- The graph can resolve owner, verify freshness, collect docs, draft guidance, and
  hand off to toritsugi. It may prepare submission effects, but external
  submission/payment/signature or government-record mutation requires explicit
  member consent, applicable legal/licensed boundary checks, and an audit trail.

## Query surface (member-support XRPC)

- `com.etzhayyim.ooyake.getUnit` — resolve a unit + its children/addresses/windows
- `com.etzhayyim.ooyake.resolvePath` — resolve a dotted path / atlas DID to a unit
- `com.etzhayyim.ooyake.findService` — *"where do I do procedure X near me?"* →
  procedure + window + address
- `com.etzhayyim.ooyake.startSupport` — create a member-authorized support session
  for a procedure or public-body activity; not an official government channel
- `com.etzhayyim.ooyake.searchUnits` — text/geo search; backs civic search at
  `etzhayyim.com` (`/actors` kotoba-wasm search surfaces gov units at R1)

## Status

**R1 — real verified data, committed.** The registry holds ~6,535 maintainer-verified
units (real Wikidata QIDs, body-own provenance) across every governance tier, with a
derivable GeoJSON map. What remains **gated** (operator/Council, NOT done here):

- **Live kotoba ingest** of the registry into the `gov-atlas-v1` Datom graph — needs an
  operator token + node (`KOTOBA_TOKEN`).
- **Publishing** national `:authoritative` rows to `/.well-known/gov-units.json` — the
  Council-Lv6+ / bootstrap-attestation gate (`validate_atlas.py` check #5 currently
  admits only the JP prefecture/city backbone as published-authoritative).

i.e. this is the committed registry **record** of real data; ingest + public
publication are the separate operator/Council steps. See [`MATURITY.md`](MATURITY.md)
for the full per-iteration build log.
