## What and why
<!-- The user story (NX-...) or bug this closes, and what changes for the user. -->
Closes #

## How it was tested
- [ ] `./mvnw verify` (or the module's tests) passes
- [ ] `npm run lint` and `npm run format:check` pass for the web application(s) changed
- [ ] Checked by hand in the browser (light and dark mode, phone width) when screens changed
- [ ] `node scripts/smoke-test.mjs` passes against a running stack when APIs changed

## Checklist (definition of done)
- [ ] New behaviour has tests; errors use the standard JSON error body
- [ ] New endpoints appear in Swagger and are protected by the right role
- [ ] No secrets, passwords or keys in the code, the tests or the logs
- [ ] Docs, demo data or Kubernetes manifests updated if needed
