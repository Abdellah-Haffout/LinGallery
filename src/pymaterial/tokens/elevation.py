from enum import Enum

class ElevationLevel(Enum):
    LEVEL_0 = 0.0
    LEVEL_1 = 1.0
    LEVEL_2 = 3.0
    LEVEL_3 = 6.0
    LEVEL_4 = 8.0
    LEVEL_5 = 12.0

class ElevationRole(Enum):
    SURFACE = ElevationLevel.LEVEL_0
    RAISED = ElevationLevel.LEVEL_1
    FLOATING = ElevationLevel.LEVEL_3
    DIALOG = ElevationLevel.LEVEL_3
    MODAL = ElevationLevel.LEVEL_4
    OVERLAY = ElevationLevel.LEVEL_5
