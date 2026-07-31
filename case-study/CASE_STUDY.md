# Case Study Scenarios to discuss

## Scenario 1: Cost Allocation and Tracking
**Situation**: The company needs to track and allocate costs accurately across different Warehouses and Stores. The costs include labor, inventory, transportation, and overhead expenses.

**Task**: Discuss the challenges in accurately tracking and allocating costs in a fulfillment environment. Think about what are important considerations for this, what are previous experiences that you have you could related to this problem and elaborate some questions and considerations

**Questions you may have and considerations:**

The first question is not technical: **what decision does this cost data change?** "Track costs accurately" is not a requirement — deciding whether to close a warehouse, renegotiate a carrier contract, or charge a store for the fulfilment it consumes are requirements, and each implies a different granularity. I would want to know who the consumer is (finance controlling, warehouse managers, commercial), because a chargeback number that lands on someone's P&L must be defensible and frozen, while a management-insight number can be restated next month. Building the defensible version when only the insight version was needed is the most common way this kind of project doubles in cost.

Second, **where does the money actually live today?** The system in front of us is warehouse and store master data — `Warehouse` carries `businessUnitCode`, `location`, `capacity`, `stock` and timestamps, and no monetary attribute at all. Actuals live in the ERP general ledger, labour in WMS/time systems, freight in TMS or carrier invoices. The sane split is: this system owns the *dimension* (which warehouses and stores exist, where, at what capacity, and for how long), finance owns the *amounts*. If we start posting amounts here we have quietly built a second ledger that will disagree with the first one.

The genuinely hard part is **indirect cost**. Labour and transport that serve several stores from one warehouse must be allocated on a driver — m³-days stored, order lines picked, drops per route, headcount hours. Choosing the driver is a business policy decision, not an engineering one, and people whose numbers get worse will contest it, so the rule needs an owner, a version, and an audit trail. Related: is inventory a cost of the warehouse at all, or working capital owned by merchandising?

Remaining questions: do finance cost centres map 1:1 to business unit codes, or is a mapping table needed? What is the close cadence and materiality threshold? Are capacity and location changes effective-dated, so that a restated prior period still allocates on the values that were true then? And what accuracy is actually good enough — an 80%-right allocation delivered monthly beats a perfect one nobody trusts or maintains.

## Scenario 2: Cost Optimization Strategies
**Situation**: The company wants to identify and implement cost optimization strategies for its fulfillment operations. The goal is to reduce overall costs without compromising service quality.

**Task**: Discuss potential cost optimization strategies for fulfillment operations and expected outcomes from that. How would you identify, prioritize and implement these strategies?

**Questions you may have and considerations:**

Optimisation without a trusted baseline is opinion, so the first question is **what is our cost-to-serve today, per what unit?** Cost per order, per unit shipped, per m³-day stored — the denominator matters more than the numerator, because absolute cost rises with volume and tells you nothing. If Scenario 1 has not landed, most of this work is measurement, and I would say so rather than promise savings.

The second question is **which costs are actually controllable, and by whom?** A lease signed for five years is not a lever for a warehouse manager. Sorting the cost base into fixed, step-fixed and variable, and tagging who can move each, usually shrinks the candidate list dramatically and prevents the classic failure of holding people accountable for numbers they cannot influence.

Candidate levers, roughly in order of size in this domain:

- **Footprint and utilisation.** This system already knows `capacity` and `stock` per warehouse, and `Location` carries `maxNumberOfWarehouses` and `maxCapacity`. A warehouse running at 40% of capacity carries near-full fixed cost against a fraction of the throughput. A utilisation report needs no schema change at all and is the cheapest first deliverable — it points at consolidation and replacement candidates before any cost model exists.
- **Labour**, usually the largest variable block: scheduling against demand rather than against a fixed roster.
- **Transportation**: replenishment frequency and consolidation between warehouse and store, which trades directly against store stock-outs.
- **Inventory placement**: whether the right products sit in the warehouse nearest the stores that sell them.

Prioritisation: size of prize × confidence in the estimate ÷ time to value, with a bias toward reversible changes. Every candidate needs a **paired service metric agreed up front** — fill rate, replenishment lead time, OTIF — otherwise cost falls, service degrades quietly, and the saving is paid back later in lost sales.

Implementation should be run as experiments with a stated hypothesis and a measurement window, not as a programme. Two things to watch: **cost shifting**, where a saving in one business unit reappears as a cost in another and only a network-level view catches it; and benefit double-counting, which is why realisation should be tracked in the same system that produced the baseline. I would express expected outcomes as a range with a confidence level, never a single number.

## Scenario 3: Integration with Financial Systems
**Situation**: The Cost Control Tool needs to integrate with existing financial systems to ensure accurate and timely cost data. The integration should support real-time data synchronization and reporting.

**Task**: Discuss the importance of integrating the Cost Control Tool with financial systems. What benefits the company would have from that and how would you ensure seamless integration and data synchronization?

**Questions you may have and considerations:**

The value of the integration is singular: **one set of numbers**. If operations and finance argue about whose figure is right, no decision gets made and the tool becomes another spreadsheet. So the first thing to settle is **which system is the book of record** — and the answer is almost always the ERP general ledger. The cost tool consumes and reconciles to it; it does not compete with it. Any design where the two can diverge without a break report is broken.

I would push hard on the phrase **"real-time"**. Financial actuals are posted on a period close cycle; synchronising a monthly-posted ledger in real time buys nothing and costs a great deal. What genuinely benefits from low latency is the *operational driver* data — volumes, hours, capacity, utilisation — which gives early warning weeks before the close confirms it. That suggests two feeds with different SLAs and different trust levels, clearly labelled in the UI, rather than one "real-time" pipe. The question for the stakeholder is: what would you actually do differently on Tuesday if you knew on Tuesday?

The real integration risk is **master data, not transport**. Do warehouse business unit codes equal finance cost centres? If not, who owns the mapping, and what happens the moment a new warehouse is created here — is cost centre creation a manual ERP task? That is a process integration with an owner and an SLA, not an API call, and unmapped entities are where cost silently disappears. Same for period calendars, company codes, currency and FX policy.

Correctness concerns I would raise early:

- **Restatement.** Periods reopen and get adjusted. The integration must handle corrections and reversals, and reporting must answer "as of" a version rather than mutating history.
- **Idempotency and replay**, because feeds will be re-run.
- **Reconciliation controls**: automated per-period, per-cost-centre totals against the GL, with a visible break report. This is what earns finance's trust, and it is worth more than any feature.

Finally, direction of flow: is the tool ever expected to *post back* — accruals or allocation journals? That crosses into controls and audit territory and raises the bar substantially. Also worth confirming: retention requirements and who is permitted to see cost data, which is usually more restricted than operational data.

## Scenario 4: Budgeting and Forecasting
**Situation**: The company needs to develop budgeting and forecasting capabilities for its fulfillment operations. The goal is to predict future costs and allocate resources effectively.

**Task**: Discuss the importance of budgeting and forecasting in fulfillment operations and what would you take into account designing a system to support accurate budgeting and forecasting?

**Questions you may have and considerations:**

Again I would start from the decision: **what commitment does the forecast unlock?** Hiring and shift planning needs weeks of horizon at warehouse granularity; a lease or a new warehouse needs quarters at network granularity; a pricing decision needs cost per unit. The horizon and grain fall out of that, and getting it wrong in either direction is expensive — monthly forecasts per warehouse per cost category is a very large number of cells that nobody will maintain past the second quarter.

The central design principle is to **forecast drivers, not costs**. Cost equals driver volume times rate, so the model should project demand (orders, units, m³) and then apply cost behaviour: fixed, step-fixed, variable. This matters concretely here because warehouse capacity is **step-fixed**, not linear. `Location` constrains both `maxNumberOfWarehouses` and `maxCapacity`, so extra capacity arrives as a discrete event — open, replace, or archive a warehouse — with a step change in fixed cost and often a location ceiling that blocks it entirely. A useful forecasting system therefore has to model **planned** warehouses, not just existing ones, which is a real extension to a domain model that only knows about warehouses that already exist.

Key question: **is there already a demand forecast?** If commercial or S&OP produces one, we consume it. Building a second demand forecast inside a cost tool guarantees that finance and operations plan off different numbers, and that argument is unwinnable.

Other things I would want settled:

- **Annual budget versus rolling forecast** — different cadences, different owners, different accuracy expectations. Which one is this?
- **Versioning.** Budget v1, reforecasts, actuals: versions must be frozen and comparable, or variance analysis is meaningless.
- **Bottom-up versus top-down**, and the reconciliation process when they disagree — that reconciliation is usually the actual business process being requested.
- **Forecast accuracy measurement** (bias and MAPE by cost category). Without it the forecast never improves and nobody knows which parts to trust.
- **Scenarios over point precision.** "Volume +15% and we do not open a second Tilburg warehouse" is more valuable than a single number carried to two decimals.
- Seasonality and peak, contractual indexation and inflation, FX if the network is multi-country.

Spurious precision is the main risk. I would deliberately start coarse and let variance analysis tell us where finer grain earns its keep.

## Scenario 5: Cost Control in Warehouse Replacement
**Situation**: The company is planning to replace an existing Warehouse with a new one. The new Warehouse will reuse the Business Unit Code of the old Warehouse. The old Warehouse will be archived, but its cost history must be preserved.

**Task**: Discuss the cost control aspects of replacing a Warehouse. Why is it important to preserve cost history and how this relates to keeping the new Warehouse operation within budget?

**Questions you may have and considerations:**

Replacement in this system archives the existing warehouse — `archivedAt` is set, the row is never deleted — and creates a new one reusing the same `businessUnitCode`. That gives us two identities, and the first design question is **which one costs attach to**: the business unit code, or the individual warehouse instance.

The answer has to be *both, recorded at instance grain*. Post costs against the instance and roll up to the code. Only the instance level can answer "is the new warehouse cheaper to run than the one it replaced"; only the code level gives finance a continuous cost centre time series across generations. Posting at code level is the trap, because the split can never be reconstructed afterwards, whereas the aggregate is always derivable from the detail.

That leads directly to the reporting hazard: a naive `group by business_unit_code` **silently merges two generations**. If MWH.012 is replaced by a facility with double the capacity, the code's cost line steps up and looks like a blow-out; halve the capacity and it looks like a saving. Neither is true — it is a different physical asset. Trends over a code must be annotated with the replacement event and normalised (cost per m³ of capacity, cost per unit shipped) rather than shown in absolutes, or every replacement manufactures a false variance someone spends a week explaining.

Preserving history is what makes the new warehouse's budget defensible. The predecessor's steady-state run rate, normalised for capacity and volume and stripped of one-offs, *is* the baseline. Without it the new warehouse is budgeted from a business case nobody can test. Equally, the transition must not pollute the run rate: dual running while both are live, stock migration, ramp-up inefficiency, decommissioning and lease exit, possible write-offs. Those are project costs, and I would want them flagged as such from day one.

The schema reality: `Warehouse` carries only `businessUnitCode`, `location`, `capacity`, `stock`, `createdAt`, `archivedAt` — **no cost attributes whatsoever**. This is a genuine schema extension, not a reporting change. It needs a stable instance identity (today the natural key is code + `createdAt`; a surrogate id would be safer), an effective-dated link from instance to code, and a cost fact keyed on instance × period × category. I would also add an explicit predecessor reference, so baseline comparison is a join rather than a guess based on ordering by `createdAt`.

Questions for the business: can a code be replaced more than once, and do we need a generation sequence? Can location change on replacement — and if it does, is it still the same cost centre to finance? Do late invoices posted after `archivedAt` still attach to the archived generation (they must)? And is there a business case with a payback that the tool is expected to track realisation against? That is usually the real request hiding behind "keep it within budget".

## Instructions for Candidates
Before starting the case study, read the [BRIEFING.md](BRIEFING.md) to quickly understand the domain, entities, business rules, and other relevant details.

**Analyze the Scenarios**: Carefully analyze each scenario and consider the tasks provided. To make informed decisions about the project's scope and ensure valuable outcomes, what key information would you seek to gather before defining the boundaries of the work? Your goal is to bridge technical aspects with business value, bringing a high level discussion; no need to deep dive.
