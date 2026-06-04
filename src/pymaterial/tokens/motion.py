from enum import Enum, auto
from dataclasses import dataclass

class EasingType(Enum):
    STANDARD = auto()
    EMPHASIZED = auto()
    LINEAR = auto()
    EMPHASIZED_DECELERATE = auto()
    EMPHASIZED_ACCELERATE = auto()
    STANDARD_DECELERATE = auto()
    STANDARD_ACCELERATE = auto()

@dataclass(frozen=True)
class MotionDuration:
    fast_ms: int
    normal_ms: int
    slow_ms: int

@dataclass(frozen=True)
class SpringSpec:
    stiffness: float
    damping_ratio: float

@dataclass(frozen=True)
class MotionSpec:
    durations: MotionDuration
    spring_standard: SpringSpec
    spring_emphasized: SpringSpec
    enter_easing: EasingType
    exit_easing: EasingType

class MotionSpecs:
    @staticmethod
    def baseline() -> MotionSpec:
        return MotionSpec(
            durations=MotionDuration(fast_ms=200, normal_ms=300, slow_ms=500),
            spring_standard=SpringSpec(stiffness=600.0, damping_ratio=1.0),
            spring_emphasized=SpringSpec(stiffness=350.0, damping_ratio=0.8),
            enter_easing=EasingType.EMPHASIZED_DECELERATE,
            exit_easing=EasingType.EMPHASIZED_ACCELERATE,
        )
