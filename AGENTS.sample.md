# AGENTS.md

This project uses `x-maestro` tags and Maestro flows as part of normal product development. If you are an AI agent working in this repo, follow these rules on every UI change.

## Goals

- Keep `x-maestro` tags present for UI that matters to product behavior.
- Keep `x-maestro` tags aligned with the real production UI at all times.
- Write and run Maestro flows while implementing changes, not after the fact.

## What To Tag

Add or update `x-maestro` tags for any UI that is important to test. This includes:

- Primary user journeys such as sign in, checkout, search, filtering, onboarding, settings, and destructive actions.
- UI that is business-critical, easy to regress, or expensive to verify manually.
- Repeated UI patterns where test intent is clearer through a named command than through fragile selectors.
- Cross-screen actions that should be expressed once and reused in flows.

Do not add tags for purely decorative UI or for interactions that provide no testing value.

## Tagging Rules

- Use `<x-maestro>` for reusable test actions exposed by the UI.
- Keep each tag close to the production UI it describes so it changes with the feature.
- The command body must describe the real user interaction path through the current UI.
- Prefer stable command names that describe intent, such as `login`, `openFilters`, or `search`.
- When the UI is dynamic, prefer parameterized names such as ``toggleFilter: ${filterId}`` or aliases such as ``[`viewProduct${i}`, `viewProduct: ${productId}`]``.
- If production code changes the interaction path, labels, ids, or required arguments, update the `x-maestro` tag in the same change.
- Do not leave stale tags in place after UI refactors.

## Sync Requirement

`x-maestro` tags are part of the production contract for test automation. Treat them like any other maintained interface.

Whenever you change:

- copy shown to the user
- DOM structure
- control ids
- navigation flow
- required inputs
- feature naming

you must check whether an `x-maestro` tag or Maestro flow is affected and update it in the same task.

## Flow Authoring Rules

- Prefer custom commands in flows when they make the test more readable.
- Use `$commandName` invocation syntax for custom commands.
- Use named args when the action is clearer with explicit keys.
- Use positional args only for simple one-argument commands.
- Keep flows focused on user-visible behavior, not implementation details.
- Prefer a small number of reusable commands over repeating brittle low-level steps.

Example:

```yaml
- $login:
    email: leland@mobile.dev
    password: ${PASSWORD}
- $openFilters
- $toggleFilter: men
- assertVisible: Results
```

## Required Workflow For UI Changes

When implementing any feature or fixing any bug in UI code:

1. Identify whether the change touches behavior that should be testable through Maestro.
2. Add or update the relevant `x-maestro` tags in the production UI.
3. Add or update Maestro flows that cover the changed behavior.
4. Run the affected Maestro flow during implementation.
5. If the flow fails, fix either the product code, the `x-maestro` tag, or the flow before considering the task complete.

Do not defer flow updates or test execution to a later cleanup step.

## Definition Of Done

A UI task is not complete unless all of the following are true:

- Production code includes the necessary `x-maestro` tags for important behavior.
- Any changed tags still match the real UI behavior.
- Maestro flows exist for the implemented or changed behavior.
- Those flows were run during iteration and pass.

## Review Checklist

Before finishing a task, verify:

- Are new important interactions missing `x-maestro` tags?
- Did any existing `x-maestro` tag become inaccurate because of this change?
- Is there a Maestro flow that proves the behavior works?
- Was that flow actually run after the latest code change?

If the answer to any of these is no, the task is incomplete.

## Agent Priority Rules

If there is a conflict between speed and maintaining test coverage:

- prefer updating `x-maestro` tags now
- prefer updating or writing the flow now
- prefer running the flow now

The expected development loop is:

1. implement
2. tag
3. write or update flow
4. run flow
5. iterate until green

## Notes For Agents

- Do not invent `x-maestro` tags that bypass the real UI behavior.
- Do not keep legacy tags just to preserve old tests when the product behavior has changed.
- Do not ship UI changes with unverified flows when a flow can reasonably be run in the current environment.
- If a flow cannot be run, say so explicitly and explain what blocked verification.
