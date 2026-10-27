# LTE Guard

Keeps mobile data on plain 4G (no 4.5G / LTE-A carrier aggregation) on a Xiaomi/Redmi phone.

- Detects the state instantly through TelephonyCallback display info
  (overrideNetwork = LTE_CA is exactly the 4.5G status-bar icon).
- Fixes it with Shizuku (shell privileges): locks LTE to chosen bands through the hidden
  MiuiBandMode screen (uiautomator + input tap), then cycles mobile data. Rate limited.
- Failsafe: no cellular internet 90 s after a band lock -> bands restored, lock disabled.
