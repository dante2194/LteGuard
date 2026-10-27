# LTE Guard

Keeps mobile data on plain 4G (no 4.5G / LTE-A carrier aggregation) on a Xiaomi/Redmi phone.

- Detects the state instantly through TelephonyCallback display info
  (overrideNetwork = LTE_CA is exactly the 4.5G status-bar icon).
- Fixes it with Shizuku (shell privileges) by cycling mobile data so the modem re-attaches.
  Rate limited: 1 per 90 s, 10 per hour, longer off-time after repeated failures.
- Band lock was removed: the hidden MiuiBandMode screen crashes the SIM on this device.
