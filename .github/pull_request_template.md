## Historia de usuario

Closes #<!-- número del issue de la historia, p. ej. Closes #8 -->

**HU-XX — Título de la historia**

## Cambios

<!-- Qué se hizo y por qué. Enumere los cambios relevantes por capa. -->

- **Backend:**
- **Frontend:**
- **Base de datos (migraciones Flyway):**

## Criterios de aceptación

<!-- Copie los criterios del issue y marque los que se cumplen. -->

- [ ] **CA1:** Dado …, cuando …, entonces …
- [ ] **CA2:** Dado …, cuando …, entonces …

## Pruebas

- [ ] Pruebas unitarias agregadas o actualizadas
- [ ] Pruebas de integración con Testcontainers (extienden `AbstractIntegrationTest`)
- [ ] `.\mvnw.cmd verify` en verde (backend)
- [ ] `npx ng test --watch=false` en verde (frontend)
- [ ] CI de GitHub Actions en verde

<!-- Describa cómo probar manualmente el cambio, si aplica. -->

## OpenAPI

- [ ] Los endpoints nuevos o modificados aparecen en `/swagger-ui.html` con descripciones y códigos de respuesta
- [ ] No aplica (sin cambios en la API)

## Checklist de calidad

- [ ] Código e identificadores en inglés; textos visibles para el usuario en español (CLAUDE.md)
- [ ] Sin datos personales ni clínicos en logs
- [ ] Sin secretos en el código; variables nuevas agregadas a `.env.example`
- [ ] Cambios de esquema en una migración Flyway **nueva** (no se editaron migraciones aplicadas)
- [ ] Commits con Conventional Commits y número de issue

## Capturas de pantalla

<!-- Arrastre aquí capturas o GIF de las pantallas afectadas (antes / después). Borre la sección si no aplica. -->
