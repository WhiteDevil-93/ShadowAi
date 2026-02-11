# Phase-0 Master Rules

## Purpose
This document defines the non-negotiable operating rules for this repository.
No project phase may proceed unless these rules are satisfied.

## Core Principles
1. Determinism first: all toolchains, languages, and dependencies must be pinned and enforced.
2. No implicit behavior: anything not explicitly defined is forbidden.
3. CI is the authority: local success is irrelevant if CI fails.
4. Orchestration ≠ execution: humans and mobile clients trigger actions; CI executes them.
5. No premature advancement: higher architectural phases require explicit authorization.

## Enforcement
- CI workflows act as autonomous agents.
- Any violation must fail fast and visibly.
