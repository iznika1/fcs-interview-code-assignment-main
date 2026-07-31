# Session brief: w1-c2-casestudy

Open a Claude Code session with cwd `../w1-c2-casestudy` and paste everything below the line.

---

You are worker `w1-c2-casestudy`, one of six running in parallel on isolated git worktrees. You are on branch `docs/case-study`.

## Your lane — touch nothing else, write no code

```
case-study/CASE_STUDY.md
```

Answer the five scenarios by replacing each `[ fill here your answer ]` placeholder. Keep all existing headings and structure intact. This is a pure business and architecture discussion. Read `case-study/BRIEFING.md` first for the domain.

## The actual rubric

From the bottom of `CASE_STUDY.md`: *"To make informed decisions about the project's scope and ensure valuable outcomes, what key information would you seek to gather before defining the boundaries of the work? Your goal is to bridge technical aspects with business value, bringing a high level discussion; no need to deep dive."*

So each answer should do three things: raise the clarifying questions you'd ask a stakeholder, name the key considerations and trade-offs, and connect the technical approach to business value. **Asking sharp questions scores better here than asserting a confident design.** Roughly 250–400 words per scenario. Write for a senior engineering audience; avoid filler and buzzwords.

The five scenarios: cost allocation and tracking; cost optimisation strategies; integration with financial systems; budgeting and forecasting; and cost control in warehouse replacement.

## Scenario 5 — make this one concrete

It connects directly to this system's actual domain model. A warehouse replacement archives the old warehouse (`archivedAt` is set, the row is never deleted) and creates a new one reusing the same business unit code. So the business unit code is a **stable cost centre across physical replacements**, while each warehouse generation is a distinct row.

Discuss what that implies for cost history: whether costs attach to the business unit code or the individual warehouse instance; how you'd compare a new warehouse's run rate against its predecessor's baseline; and the risk of a naive `group by business_unit_code` silently merging two generations with very different capacity.

Also note that the domain model currently carries only `createdAt` and `archivedAt` and **no cost attributes at all**, so any cost tracking is a genuine schema extension rather than a reporting change.

## Commit

```bash
git add -A && git commit --author="iznika <solutions@attrilio.com>" -m "docs(case-study): answer cost control scenarios"
```

No `Co-Authored-By` or attribution trailer — firm user preference. Verify with `git log -1 --pretty=format:"%(trailers)"`, which must print `[]`. Confirm you stayed in your lane with `git diff --name-only master`.
