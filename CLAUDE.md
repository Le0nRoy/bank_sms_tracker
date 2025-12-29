# Claude Code Guidelines

**This file redirects to the main AI agent guidelines.**

All rules and guidelines for Claude Code (and other AI agents) are maintained in a single source of truth:

**→ See: [AGENTS.md](AGENTS.md)**

## Why This Redirect?

- Single source of truth for all AI agents
- Easier maintenance - update one file instead of many
- Consistent rules across Claude, Codex, Cursor, and other agents
- Reduces duplication and potential conflicts

## Quick Reference

The main guidelines in `AGENTS.md` cover:

1. **Architecture** - Entry points, persistence, domain model
2. **Coding Rules** - Kotlin style, payment parsing, config editing
3. **Testing Expectations** - Unit, integration, and E2E testing
4. **Operational Notes** - Build commands and test execution

## Critical Rules (Summary)

These are the most important rules - see `AGENTS.md` for full details:

1. **Read files before modifying** - Never propose changes to unread code
2. **Match existing code style** - Follow Kotlin nullability and data class conventions
3. **Update both assets and test fixtures** - When modifying config models
4. **Preserve test broadcast pathway** - Keep debug extras for instrumentation tests
5. **Use ConfigRepository helpers** - For all config reads/updates
6. **Run tests before committing** - `./gradlew test` for unit, connectedAndroidTest for E2E

---

**For complete guidelines**: Read `AGENTS.md` in this repository
