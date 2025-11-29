# E2E Test Infra: PTU-Ticket High Concurrency Railway Ticketing System

## Test Philosophy
- Opaque-box, requirement-driven. No dependency on implementation design.
- Methodology: Category-Partition + BVA + Pairwise + Workload Testing (4-tier methodology).

## Feature Inventory
| # | Feature | Source (requirement) | Tier 1 | Tier 2 | Tier 3 | Tier 4 |
|---|---------|---------------------|:------:|:------:|:------:|:------:|
| 1 | Ticket Query | GET /api/tickets/query | 5 | 5 | ✓ | ✓ |
| 2 | Ticket Order | POST /api/tickets/order | 5 | 7 | ✓ | ✓ |

## Test Architecture
- Test runner: D:\PTU-Ticket\tests\e2e\run_e2e.py
  - Spawns the mock server in a separate process, waits for it to become ready, runs the test suite, and gracefully stops the server.
- Test case format: Python standard unittest.TestCase in D:\PTU-Ticket\tests\e2e\test_e2e.py.
- Mock target: D:\PTU-Ticket\tests\e2e\mock_server.py
  - FastAPI server running on http://127.0.0.1:8080.
  - Maintains in-memory thread-safe state (via a mutex lock) and implements full request validation.
- Directory layout:
  `
  D:\PTU-Ticket\tests\e2e\
  ├── mock_server.py
  ├── test_e2e.py
  └── run_e2e.py
  `

## Real-World Application Scenarios (Tier 4)
| # | Scenario | Features Exercised | Complexity |
|---|----------|--------------------|------------|
| 1 | Complete Booking Workflow | Ticket Query, Ticket Order, Stock Depletion | Medium |
| 2 | Concurrent Booking Depletion | Ticket Query, Ticket Order, Concurrent Lock Handling | High |

## Coverage Thresholds
- Tier 1: ≥5 per feature (Total: 10 tests)
- Tier 2: ≥5 per feature (Total: 12 tests)
- Tier 3: Pairwise coverage of feature interactions (Total: 4 tests)
- Tier 4: Real-world workloads and concurrency verification (Total: 3 tests)
- Total E2E Tests: 29 tests
