# Contributing

Convo is a one-maintainer project, but issues and pull requests are welcome.

## Workflow

- `main` is always releasable. Branch off it, keep changes focused.
- Commit messages are short, imperative summaries (`Fix ...`, `Add ...`).
- The `versionName` in `app/build.gradle.kts` is the source of truth. Beta tags look like `v1.2.1-beta1`, stables like `v1.2.1`. The `scripts/release.sh` script handles tagging and publishing — don't tag by hand.
- After a stable, the next cycle starts at `1.x.y-beta1`.

## Code

- Kotlin + Jetpack Compose, single `:app` module.
- Match the existing structure (`features/chat/{model,data,state}`, `ui/...`, `core/...`).
- Behavior-preserving refactors are welcome; behavior changes need a device-tested note in the PR.
- No new system-level dependencies or permission changes without discussion in the issue first.

## Reporting bugs

Use the bug template: what happened, steps to reproduce, expected behavior, app version (bottom of Settings), device + Android version. Screenshots or screen recordings for UI issues.
