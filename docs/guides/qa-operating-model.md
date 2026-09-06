# QA operating model

This is the audience guide for QA: the mental model, the normal workflow, the high-value
scenarios and what a useful bug report contains.
[`helix-kmp-source-of-truth.md`](../architecture/helix-kmp-source-of-truth.md) remains the
normative source; subsections keep their original `32.n` numbers.

---


## 32.1 QA mental model

QA should be able to identify:

```text
Screen/Cell instance
ResourceKey
current fixture/state
refresh/freshness state
live connection/subscriber state
last Action/Output
```

without reading mobile internals.

## 32.2 Normal QA workflow

1. reproduce using fixture gallery where possible;
2. identify Cell/Screen and state;
3. test offline/refresh/reconnect where relevant;
4. capture inspector/flight-recorder summary;
5. attach expected vs actual screenshot/behavior;
6. report RouteKey/FeatureInstanceKey/ResourceKey when available.

## 32.3 High-value scenarios

For stateful Cells:

- Loading/content/error/offline;
- same Cell twice;
- move/reuse across surfaces;
- scroll off/on;
- logical removal;
- process restart;
- large font/dark/compact/expanded;
- kill switch/registry inclusion;
- live reconnect;
- resource shared by multiple observers.

## 32.4 Better bug report example

```text
Surface: Home
Cell: LiveScoreCell
FeatureInstanceKey: home-feed/live-score/slot-12
ResourceKey: match-123
Observed: Cell shows stale score after socket reconnect
Inspector: subscribers=2, socket=connected, last REST refresh=31s,
           last socket sequence did not advance after reconnect
Expected: both Home and Article instances converge to latest score
```

This is far more actionable than "score widget sometimes stale."
