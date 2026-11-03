# Agent instructions

## Before opening a pull request
Self-review the branch with the `review-recipe-pr` skill: run `/review-recipe-pr` in Claude Code, or read and follow `.claude/skills/review-recipe-pr/SKILL.md` in other agents.
Fix what it finds, and answer its "Is this the right change?" questions in the pull request description.

Keep pull requests small and focused on one migration step for a pattern that is common in practice, with tests for the before and after.

## Reviewing a pull request
Use the same skill when asked to review or polish a contributor's recipe pull request.
