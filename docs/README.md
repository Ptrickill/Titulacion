# Rehab Elite — Documentation

Index of the project documentation. Diagram labels and prototype screens are in Spanish, as is the functional documentation; this index is in English like the rest of the technical documentation.

## Contents

| Path | Contents |
|---|---|
| [`diagrams/`](diagrams/) | Architecture and design diagrams (PNG) used in the thesis: C4 levels 1–3, domain classes, sessions module classes and entity-relationship model |
| [`diagrams/source/`](diagrams/source/) | Editable sources of the diagrams: PlantUML files (`*.puml`), shared styles (`c4-style.iuml`, `uml-style.iuml`) and the custom renderer used for figure 14 |
| [`prototype/`](prototype/) | High-fidelity prototype: 14 screens grouped by role |
| [`evidence/`](evidence/) | Sprint evidence (boards, burndown, pull requests, CI runs, tests, screens, minutes), one folder per sprint |
| [`user-stories.md`](user-stories.md) | User stories with acceptance criteria (Given / When / Then) |

## Figures

| Fig. | Title | Image | Source |
|---|---|---|---|
| 12 | C4 Level 1 — System context | [fig12-c4-level1-context.png](diagrams/fig12-c4-level1-context.png) | [fig12-c4-level1-context.puml](diagrams/source/fig12-c4-level1-context.puml) |
| 13 | C4 Level 2 — Containers | [fig13-c4-level2-containers.png](diagrams/fig13-c4-level2-containers.png) | [fig13-c4-level2-containers.puml](diagrams/source/fig13-c4-level2-containers.puml) |
| 14 | C4 Level 3 — Components | [fig14-c4-level3-components.png](diagrams/fig14-c4-level3-components.png) | [fig14-c4-level3-components.puml](diagrams/source/fig14-c4-level3-components.puml), [render script](diagrams/source/fig14_c4_level3_components_render.py) |
| 15 | Domain class model | [fig15-domain-class-model.png](diagrams/fig15-domain-class-model.png) | [fig15-domain-class-model.puml](diagrams/source/fig15-domain-class-model.puml) |
| 16 | Sessions module classes | [fig16-sessions-module-classes.png](diagrams/fig16-sessions-module-classes.png) | [fig16-sessions-module-classes.puml](diagrams/source/fig16-sessions-module-classes.puml) |
| 17 | Entity-relationship model | [fig17-entity-relationship-model.png](diagrams/fig17-entity-relationship-model.png) | [fig17-entity-relationship-model.puml](diagrams/source/fig17-entity-relationship-model.puml) |

The entity-relationship model (fig. 17) mirrors the Flyway migrations in `backend/src/main/resources/db/migration/`, which remain the source of truth for the data model.

### Regenerating the diagrams

The diagrams are generated with [PlantUML](https://plantuml.com/) from `diagrams/source/`. Each source file has the same name as its PNG, and the C4 figures use the C4-PlantUML standard library bundled with PlantUML. With Docker, from the repository root:

```powershell
docker run --rm -v "${PWD}/docs/diagrams/source:/data" -w /data plantuml/plantuml -tpng -charset UTF-8 "*.puml"
Move-Item docs/diagrams/source/*.png docs/diagrams/ -Force
```

Figure 14 is the exception: the published PNG uses a hand-tuned layout drawn by `fig14_c4_level3_components_render.py` (Python + Playwright), while `fig14-c4-level3-components.puml` is the equivalent PlantUML version. The script still contains absolute paths from the environment where it was written (output directory and Chromium executable); adjust them before running it.

## Prototype screens

| Role | # | Screen | Image |
|---|---|---|---|
| All roles | 01 | Login | [01-login.jpg](prototype/01-login.jpg) |
| Physiotherapist | 02 | Dashboard | [02-physio-dashboard.jpg](prototype/02-physio-dashboard.jpg) |
| Physiotherapist | 03 | Patients | [03-physio-patients.jpg](prototype/03-physio-patients.jpg) |
| Physiotherapist | 04 | Clinical record | [04-physio-clinical-record.jpg](prototype/04-physio-clinical-record.jpg) |
| Physiotherapist | 05 | Session timeline | [05-physio-timeline.jpg](prototype/05-physio-timeline.jpg) |
| Physiotherapist | 06 | Therapy session form | [06-physio-session-form.jpg](prototype/06-physio-session-form.jpg) |
| Physiotherapist | 07 | AI summary assistant | [07-physio-ai-assistant.jpg](prototype/07-physio-ai-assistant.jpg) |
| Administrator | 08 | Dashboard | [08-admin-dashboard.jpg](prototype/08-admin-dashboard.jpg) |
| Administrator | 09 | Users | [09-admin-users.jpg](prototype/09-admin-users.jpg) |
| Administrator | 10 | Patients | [10-admin-patients.jpg](prototype/10-admin-patients.jpg) |
| Administrator | 11 | Patient registration | [11-admin-patient-registration.jpg](prototype/11-admin-patient-registration.jpg) |
| Administrator | 12 | Reports | [12-admin-reports.jpg](prototype/12-admin-reports.jpg) |
| Operations director | 13 | Reports | [13-director-reports.jpg](prototype/13-director-reports.jpg) |
| Patient | 14 | Patient portal | [14-patient-portal.jpg](prototype/14-patient-portal.jpg) |
