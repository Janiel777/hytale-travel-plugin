
# Hytale Travel Plugin

## Overview

Hytale Travel Plugin is a custom multi-world travel system designed for a distributed Hytale server architecture.

It enables players to move between different server instances (worlds) while maintaining a consistent and synchronized inventory state through a centralized backend.

The plugin integrates with:

- A custom proxy layer for connection routing
- A lock-based backend service for inventory authority
- Multiple Hytale server instances

This creates a seamless cross-server travel experience.

---

## What This Plugin Does

The plugin introduces:

- A custom portal block
- A server-side UI that lists available worlds
- Secure cross-server travel handling
- Inventory session synchronization

When a player interacts with a portal:

1. A custom UI opens displaying available destination servers.
2. The current server saves and releases the player's inventory session.
3. The proxy redirects the player to the selected server.
4. The destination server acquires ownership of the inventory session.

This guarantees that only one server has authority over a player's inventory at any time.

---

## Architecture Role

This plugin is part of a larger multi-server ecosystem:

- Travel Plugin (this repository) → Handles UI and server travel logic
- Proxy → Routes players between server instances
- Backend → Enforces inventory session locking

The plugin acts as the bridge between in-game interactions and distributed server coordination.

---

## Technical Highlights

- Java / Gradle project
- Custom Hytale server plugin
- Asset-driven UI interactions (no per-tick polling logic)
- Database-backed lock validation via HTTP backend
- Deterministic inventory ownership across servers
- Clean separation of concerns between gameplay, networking, and persistence

---

## Why This Project Matters

This project demonstrates:

- Distributed system design within a game environment
- Cross-server state synchronization
- Network-level coordination with a proxy layer
- Backend-driven authority enforcement
- Scalable multi-instance architecture

It showcases practical experience building coordinated systems across multiple services rather than a standalone game modification.
