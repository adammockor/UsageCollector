# UsageCollector

A small Android app that collects foreground app usage and exports daily CSVs to `Downloads/UsageCollector` folder.

## How It Works
1. A periodic WorkManager job reads `UsageStatsManager` events.
2. `UsageSessionProcessor` turns events into usage intervals and daily totals.
3. Totals and intervals are stored internally; a daily export writes CSVs to `Downloads/UsageCollector`.

## Permissions & Setup
- You must grant **Usage Access** in Settings for collection to work.
- CSV exports land in `Downloads/UsageCollector/`.

