# Repository Guidelines

## Project Structure & Module Organization
- Keep runtime code under `src/`, grouping VPN provider adapters in `src/providers/` and measurement utilities in `src/metrics/`.
- Mirror that layout in `tests/` (e.g., `tests/providers/test_acme.py`) to keep specs close to the code they verify.
- Store reusable fixtures in `tests/fixtures/` and sample capture files in `assets/` so large artifacts stay out of source folders.
- Track environment defaults in `config/defaults.yaml`; never commit machine-specific secrets—use `config/local.example.env` as the template for new values.

## Build, Test, and Development Commands
- `python -m venv .venv && .venv\\Scripts\\Activate.ps1`: provision a local Python environment aligned with CI.
- `pip install -r requirements.txt`: install pinned dependencies for both app and tests.
- `pytest -q`: run the end-to-end and unit suites; add `--maxfail=1` during quick iterations.
- `ruff check src tests` and `black src tests`: lint and format before pushing to keep diffs mechanical.

## Coding Style & Naming Conventions
- Target Python 3.12, 4-space indents, and Black’s default 100-character line limit.
- Use descriptive module names (`vpn_probe.py`, not `utils2.py`) and CapWords for classes (`LatencyProbe`).
- Prefer explicit imports (`from src.metrics.latency import LatencyProbe`) over star imports to clarify dependencies.
- Document non-obvious logic with short docstrings; rely on type hints for all public functions.

## Testing Guidelines
- Write tests with `pytest`; each new feature needs at least one unit test plus an integration check if it touches provider flows.
- Name files `test_<module>.py` and functions `test_<behavior>`; fixtures go in `conftest.py` or `tests/fixtures/*.py`.
- Maintain ≥85% line coverage (`pytest --cov=src --cov-report=term-missing`); justify gaps in the PR description.

## Commit & Pull Request Guidelines
- Use Conventional Commits (`feat: add openvpn latency probe`) so changelog generation remains predictable.
- Keep commits scoped to a single concern; refactors that move files should be isolated from behavioral changes.
- Pull requests must include what/why context, mention related issue IDs, and attach screenshots or log excerpts for user-visible changes.
- Ensure CI passes before requesting review; flag flaky cases in the PR so they can be triaged quickly.

## Configuration & Security Tips
- Store provider credentials in `.env.local` (ignored by git) and document placeholder keys in `config/local.example.env`.
- Review new dependencies for license compatibility; note security-sensitive updates in the PR checklist.
- Rotate captured traffic samples quarterly; remove stale or anonymized data via `assets/README.md` housekeeping tasks.
