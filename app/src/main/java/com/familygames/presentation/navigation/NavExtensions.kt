package com.familygames.presentation.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController

/**
 * Navigates only from a settled (RESUMED) destination. A child slapping the screen with the whole hand
 * produces several taps during the transition; this drops all but the first (docs/03 §0.5).
 */
fun NavController.navigateOnce(route: Any) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        navigate(route)
    }
}

/** Back to S01 from anywhere in the child zone. */
fun NavController.backToMenu() {
    if (!popBackStack(MenuRoute, inclusive = false)) {
        navigate(MenuRoute) { popUpTo(0) }
    }
}
