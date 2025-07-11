# Architecture Diagrams

This file contains all the Mermaid diagrams used in the documentation for easy reference and viewing.

## 1. Overall Architecture

```mermaid
graph TB
    subgraph "Main Orchestrator"
        A[ListContent.kt]
    end
    
    subgraph "Animation System"
        B[ListAnimations.kt]
        C[ListAnimationHooks.kt]
    end
    
    subgraph "Configuration"
        D[ListConstants.kt]
    end
    
    subgraph "UI Components"
        E[ListComponents.kt]
        F[ListStates.kt]
    end
    
    A --> B
    A --> C
    A --> D
    A --> E
    A --> F
    
    B --> C
    D --> B
    D --> C
    D --> E
    D --> F
```

## 2. Animation Strategy Pattern

```mermaid
classDiagram
    class AnimationStrategy {
        <<interface>>
        +animate(trigger, itemIndex, isActive) AnimationValue
    }
    
    class AnimationValue {
        +scale: Float
        +rotationY: Float
        +rotationZ: Float
        +translationX: Float
        +translationY: Float
        +alpha: Float
    }
    
    class AnimationType {
        <<enum>>
        FLIP
        BOUNCE
        SPIN
        ZOOM
        SLIDE
        FADE
        WAVE
        PULSE
    }
    
    class AnimationStrategyFactory {
        +create(type: AnimationType) AnimationStrategy
    }
    
    AnimationStrategy <|.. FlipAnimationStrategy
    AnimationStrategy <|.. BounceAnimationStrategy
    AnimationStrategy <|.. SpinAnimationStrategy
    AnimationStrategy <|.. ZoomAnimationStrategy
    AnimationStrategy <|.. SlideAnimationStrategy
    AnimationStrategy <|.. FadeAnimationStrategy
    AnimationStrategy <|.. WaveAnimationStrategy
    AnimationStrategy <|.. PulseAnimationStrategy
    
    AnimationStrategyFactory --> AnimationStrategy
    AnimationType --> AnimationStrategyFactory
```

## 3. Animation Hooks

```mermaid
graph LR
    subgraph "Animation Hooks"
        A[useStaggeredVisibility]
        B[useAnimationTrigger]
        C[useItemAnimation]
        D[useBreathingEffect]
        E[usePressedScale]
        F[usePulseAnimation]
        G[useRotationAnimation]
        H[useFloatingAnimation]
        I[useErrorAnimation]
    end
    
    subgraph "Dependencies"
        J[ListAnimationConfig]
        K[AnimationStrategyFactory]
    end
    
    A --> J
    B --> J
    C --> K
    D --> J
    E --> J
    F --> J
    G --> J
    H --> J
    I --> J
```

## 4. Configuration Structure

```mermaid
graph TB
    subgraph "ListAnimationConfig"
        A[Timing Constants]
        B[Scale Values]
        C[Spring Animation Values]
        D[Error Animation Values]
        E[Loading Animation Values]
    end
    
    subgraph "ListUIConfig"
        F[Padding & Spacing]
        G[Corner Radius]
        H[Elevation]
        I[Sizes]
        J[Z-Index Values]
        K[Alpha Values]
    end
    
    A --> L[Animation System]
    B --> L
    C --> L
    D --> L
    E --> L
    
    F --> M[UI Components]
    G --> M
    H --> M
    I --> M
    J --> M
    K --> M
```

## 5. UI Components Hierarchy

```mermaid
graph TB
    subgraph "Main Components"
        A[StickyHeader]
        B[ItemCard]
        C[AnimationSelector]
        D[AnimationSelectorButton]
    end
    
    subgraph "Sub-Components"
        E[ItemAvatar]
        F[ItemContent]
        G[AnimationSelectorItem]
    end
    
    subgraph "Animation Hooks"
        H[useFloatingAnimation]
        I[useItemAnimation]
        J[useBreathingEffect]
        K[usePressedScale]
    end
    
    B --> E
    B --> F
    C --> G
    A --> H
    B --> I
    B --> J
    B --> K
```

## 6. State Management

```mermaid
graph LR
    subgraph "UI States"
        A[InitialState]
        B[InProgressState]
        C[SuccessState]
        D[FailureState]
    end
    
    subgraph "Animation Hooks"
        E[usePulseAnimation]
        F[useRotationAnimation]
        G[useErrorAnimation]
    end
    
    A --> E
    B --> F
    D --> G
```

## 7. Main Orchestrator Structure

```mermaid
graph TB
    subgraph "ListContent"
        A[State Management]
        B[TopAppBar]
        C[FloatingActionButton]
        D[Background Overlay]
        E[Animation Selector]
        F[State Renderer]
    end
    
    subgraph "State Renderer"
        G[InitialState]
        H[InProgressState]
        I[SuccessState]
        J[FailureState]
    end
    
    subgraph "UI State"
        K[ListUIState]
    end
    
    A --> K
    F --> G
    F --> H
    F --> I
    F --> J
```

## 8. Data Flow

```mermaid
sequenceDiagram
    participant User
    participant ListContent
    participant ListUIState
    participant AnimationHooks
    participant UIComponents
    participant AnimationSystem
    
    User->>ListContent: Interacts with UI
    ListContent->>ListUIState: Updates state
    ListUIState->>AnimationHooks: Triggers animations
    AnimationHooks->>AnimationSystem: Gets animation values
    AnimationSystem-->>AnimationHooks: Returns AnimationValue
    AnimationHooks-->>UIComponents: Applies animations
    UIComponents-->>User: Updates UI
```

## 9. File Dependencies

```mermaid
graph TD
    A[ListContent.kt] --> B[ListAnimations.kt]
    A --> C[ListAnimationHooks.kt]
    A --> D[ListConstants.kt]
    A --> E[ListComponents.kt]
    A --> F[ListStates.kt]
    
    B --> D
    C --> B
    C --> D
    E --> C
    E --> D
    F --> C
    F --> D
```

## 10. Component Composition

```mermaid
graph TB
    subgraph "ListContent"
        A[Scaffold]
    end
    
    subgraph "TopAppBar"
        B[Title]
        C[AnimationSelectorButton]
    end
    
    subgraph "Content"
        D[Background Overlay]
        E[Animation Selector]
        F[State Renderer]
    end
    
    subgraph "State Renderer"
        G[InitialState]
        H[InProgressState]
        I[SuccessState]
        J[FailureState]
    end
    
    subgraph "SuccessState"
        K[LazyColumn]
        L[StickyHeader]
        M[ItemCard]
    end
    
    A --> B
    A --> C
    A --> D
    A --> E
    A --> F
    F --> G
    F --> H
    F --> I
    F --> J
    I --> K
    K --> L
    K --> M
```

## 11. Animation Flow

```mermaid
flowchart TD
    A[User Selects Animation] --> B[Update ListUIState]
    B --> C[Increment animationTrigger]
    C --> D[Trigger LaunchedEffect]
    D --> E[Delay based on itemIndex]
    E --> F[Increment localAnimationTrigger]
    F --> G[AnimationStrategy.animate]
    G --> H[Return AnimationValue]
    H --> I[Apply to graphicsLayer]
    I --> J[UI Updates]
```

## 12. State Transitions

```mermaid
stateDiagram-v2
    [*] --> Initial
    Initial --> InProgress: Data Loading
    InProgress --> Success: Data Loaded
    InProgress --> Failure: Error Occurred
    Success --> InProgress: Refresh
    Failure --> InProgress: Refresh
    Success --> [*]: Navigate Away
    Failure --> [*]: Navigate Away
```

## 13. Hook Dependencies

```mermaid
graph LR
    subgraph "Primary Hooks"
        A[useItemAnimation]
        B[useStaggeredVisibility]
    end
    
    subgraph "Secondary Hooks"
        C[useBreathingEffect]
        D[usePressedScale]
        E[useFloatingAnimation]
    end
    
    subgraph "State Hooks"
        F[usePulseAnimation]
        G[useRotationAnimation]
        H[useErrorAnimation]
    end
    
    A --> I[AnimationStrategyFactory]
    A --> J[ListAnimationConfig]
    B --> J
    C --> J
    D --> J
    E --> J
    F --> J
    G --> J
    H --> J
```

## 14. Configuration Impact

```mermaid
graph TB
    subgraph "ListAnimationConfig"
        A[Timing Constants]
        B[Scale Values]
        C[Spring Values]
    end
    
    subgraph "ListUIConfig"
        D[Spacing]
        E[Sizes]
        F[Elevation]
    end
    
    subgraph "Affected Systems"
        G[Animation System]
        H[UI Components]
        I[State Management]
    end
    
    A --> G
    B --> G
    C --> G
    D --> H
    E --> H
    F --> H
    G --> I
    H --> I
```

## 15. Testing Architecture

```mermaid
graph TB
    subgraph "Test Layers"
        A[Component Tests]
        B[Hook Tests]
        C[Strategy Tests]
        D[Integration Tests]
    end
    
    subgraph "Test Subjects"
        E[ItemCard]
        F[useItemAnimation]
        G[FlipAnimationStrategy]
        H[ListContent]
    end
    
    A --> E
    B --> F
    C --> G
    D --> H
```

These diagrams provide a comprehensive visual understanding of the refactored architecture. Each diagram can be viewed in any Mermaid-compatible viewer or documentation system. 