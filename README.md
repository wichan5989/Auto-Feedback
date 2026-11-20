# Kai Soundgood Feedback Assist

Read-only Midas M32/M32R OSC RTA investigation (v0.1).

## First milestone
- Connect to M32 over UDP 10023.
- Query mixer identity and RTA source settings.
- Probe meter subscriptions and log raw response addresses and blob sizes.
- Do not modify mixer settings or EQ.

Run Python 3.9+ on a computer connected to the same network:

```bash
python osc_rta.py --ip 192.168.0.100 --seconds 20
python -m unittest discover -s tests -v
```

**Important:** Receiving meter blobs does not by itself prove that they contain usable frequency-spectrum data. Confirm spectrum encoding, source selection, frequency-bin mapping and update rates against the actual console before developing feedback detection.

## Planned safety behavior
Auto Feedback OFF by default; Assist requires confirmation. No EQ write implementation until live RTA validation and explicit approval. Later Auto mode must cap total reduction to -3 dB relative to the original EQ state, avoid repeated cuts, detect stale streams and provide undo/restore.

This repository currently contains a diagnostic prototype, **not an Android APK**.
