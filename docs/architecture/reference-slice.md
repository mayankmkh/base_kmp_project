# Canonical Cricket reference slice

This is the worked end-to-end reference slice: module graph, feed model, Capability API,
ownership, multi-instance identity, and the refresh, live, command and fixture paths.
[`helix-kmp-source-of-truth.md`](helix-kmp-source-of-truth.md) remains the normative source, and section 30.4
(canonical Cell + ViewModel acquisition) stays there because it is normative; the subsections
below keep their original `30.n` numbers.

---

Cricket is the reference because it exercises the difficult parts without inventing an artificial demo.

## 30.1 Module graph

```text
:feature:home
:feature:cricket
:capability:cricket-api
:capability:cricket-impl
:storage:database
:foundation:network
:foundation:observability
:testkit:common
```

Optional pure visual extraction only if independently reused:

```text
:ui:cricket-score
```

## 30.2 Feed model

Simple Article item remains simple:

```kotlin
sealed interface FeedItem {
    val placementId: CellPlacementId

    data class Article(
        override val placementId: CellPlacementId,
        val model: ArticleCardModel,
    ) : FeedItem

    data class Cricket(
        override val placementId: CellPlacementId,
        val matchId: MatchId,
    ) : FeedItem
}
```

Rendering:

```kotlin
items(items = state.items, key = { it.placementId.value }) { item ->
    when (item) {
        is FeedItem.Article -> ArticleCard(item.model)

        is FeedItem.Cricket -> {
            val instanceKey = FeatureInstanceKey.forPlacement(
                surface = "home-feed",
                cellType = "live-score",
                placement = item.placementId,
            )

            StatefulLazyItem(
                key = instanceKey,
            ) { ownedInstanceKey ->
                LiveScoreCell(
                    matchId = item.matchId,
                    instanceKey = ownedInstanceKey,
                    onOutput = ::handleCricketOutput,
                )
            }
        }
    }
}
```

The host constructs the key once. The keyed helper passes that exact identity to its content.

Minimum semantic helper contract:

```kotlin
@Composable
fun StatefulLazyItem(
    key: FeatureInstanceKey,
    content: @Composable (FeatureInstanceKey) -> Unit,
)
```

The helper implementation may be reduced/deleted if Compose `retain` qualifies; identity handoff remains normative.

## 30.3 Capability API

```kotlin
interface CricketQueries {
    fun liveScore(matchId: MatchId): Flow<ResourceObservation<LiveScore>>
    fun match(matchId: MatchId): Flow<ResourceObservation<Match>>
}

fun interface FollowTeam {
    suspend operator fun invoke(teamId: TeamId)
}
```

## 30.4 Canonical Cell + ViewModel acquisition

Retained in the master as normative: see
[`helix-kmp-source-of-truth.md` section 30.4](helix-kmp-source-of-truth.md#304-canonical-cell--viewmodel-acquisition).

## 30.5 Ownership

```text
LiveScoreViewModel
  owns local presentation State/Action

Cricket Capability Impl
  owns match Snapshot/Live resource keyed by MatchId
  owns REST refresh policy/SyncCoordinator/SQLDelight mapping
  owns keyed WebSocket coordinator

StatefulLazyItem
  owns FeatureInstanceKey + ViewModelStore lifecycle

App
  owns RouteKey/Nav3 back stack
  assembles Koin graph
  creates DB/platform implementations
```

## 30.6 Same match, multiple instances

```text
Home Cell
  FeatureInstanceKey = home-feed/live-score/slot-12
  ResourceKey        = match-123
  expanded           = false

Article Cell
  FeatureInstanceKey = article-99/live-score/related-slot-1
  ResourceKey        = match-123
  expanded           = true
```

Both call:

```text
CricketQueries.liveScore(match-123)
```

Capability shares:

```text
one Match resource
one durable SourceOfTruth
one keyed live connection per policy
```

## 30.7 End-to-end refresh path

```text
Tap Refresh
 -> Action.Refresh
 -> LiveScoreViewModel
 -> explicit capability refresh/command path
 -> MatchSync.sync(matchId, RefreshQos.visible())
 -> REST
 -> SQLDelight/resource writer
 -> CricketQueries flow emits
 -> ViewModel derives State
 -> Compose renders
```

## 30.8 End-to-end live path

```text
Cell A + Cell B
 -> CricketQueries.liveScore(match-123)
 -> LiveScoreResource(match-123)
 -> subscriber-aware coordinator
 -> one WebSocket subscription
 -> resource writer
 -> SQLDelight/resource state
 -> both observers update
```

## 30.9 End-to-end command path

```text
Action.FollowTeam
 -> LiveScoreViewModel
 -> FollowTeam(team-7)
 -> command implementation
 -> API/local transaction
 -> update/invalidate affected resources
 -> observers re-render
```

## 30.10 Reference fixtures

Relevant fixture set can include:

```text
Loading
Upcoming
Live
Innings break
Completed
Offline/stale
Error/retry
Subscription required
Long team names
Compact
Expanded
Large font
Dark mode
```
