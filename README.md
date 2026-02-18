
# Hytale Travel Plugin

## Demo

A short video demonstration of the system in action is available here:

**Video:** [Google Drive Video Demo](https://drive.google.com/file/d/1pmrmzChyOOiOM251Cs3RnO_GB15D3oFP/view?usp=sharing)

The video shows:
- Portal interaction and UI
- Cross-server world travel
- Inventory synchronization across servers
- Lock acquisition and release flow

---

## Overview

Hytale Travel Plugin is a distributed multi-server system that allows players to travel between game worlds while preserving inventory consistency through backend-enforced locking.

Rather than functioning as a standalone game modification, this project demonstrates coordinated behavior across:

- Multiple Hytale server instances  
- A custom proxy layer for connection routing  
- A centralized backend REST API that enforces inventory ownership  

The objective is to ensure that only one server instance has authority over a player's inventory at any given time.

---

## System Workflow

When a player interacts with a custom portal block:

1. A server-side UI opens listing available destination worlds.
2. The current server calls the backend API to acquire a database lock for the player session.
3. The server saves the inventory state and releases ownership.
4. The proxy redirects the player connection to the selected server.
5. The destination server acquires the lock and restores the player state.

This workflow prevents race conditions and concurrent modifications across server instances.

---

## Architecture Components

This repository contains the Travel Plugin (client-facing logic and UI).  
The full system also includes:

- **Proxy Layer** – Handles network-level routing of client connections using a QUIC-based protocol.
- **Backend API** – Manages session locking using a PostgreSQL-backed acquire → save → release lifecycle.
- **Database** – Stores persistent player state and enforces exclusive ownership through locking.

Client → Proxy → Server → Backend API → PostgreSQL

---

## Technical Highlights

- Java / Gradle-based server plugin  
- REST API integration with backend services  
- PostgreSQL-backed lock mechanism  
- Explicit race condition handling between server instances  
- Deterministic data ownership across distributed components  
- Docker-based local environment for backend and database services  

---


## Related Repositories

This plugin is part of a larger distributed multi-server system.  
The complete architecture also includes:

- **Hytale Servers Proxy**  
  https://github.com/Janiel777/hytale-servers-proxy  
  Custom proxy layer responsible for client connection routing between
  multiple Hytale server instances using a QUIC-based transport model.

- **Hytale Backend Service**  
  https://github.com/Janiel777/hytale-backend  
  FastAPI + PostgreSQL backend that implements lease-based inventory locking,
  optimistic concurrency control, and persistent player state management.

Together, these repositories form a coordinated distributed system
handling network routing, backend synchronization, and deterministic
inventory ownership across multiple servers.

---

## Why This Project Matters

This project demonstrates practical experience with:

- Service integration via APIs  
- Distributed system coordination  
- Database-backed locking strategies  
- Network-level routing and client-server architecture  
- Maintaining data consistency across multiple service boundaries  

It reflects applied integration and system design principles within a multi-service environment.
