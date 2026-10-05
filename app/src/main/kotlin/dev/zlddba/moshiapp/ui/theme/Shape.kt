package dev.zlddba.moshiapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val MoshiShapeExtraSmall = RoundedCornerShape(8.dp)
val MoshiShapeSmall = RoundedCornerShape(12.dp)
val MoshiShapeMedium = RoundedCornerShape(16.dp)
val MoshiShapeLarge = RoundedCornerShape(20.dp)
val MoshiShapeExtraLarge = RoundedCornerShape(24.dp)
val MoshiShapePill = RoundedCornerShape(50)

val MoshiShapes = Shapes(
    extraSmall = MoshiShapeExtraSmall,
    small = MoshiShapeSmall,
    medium = MoshiShapeMedium,
    large = MoshiShapeLarge,
    extraLarge = MoshiShapeExtraLarge
)
