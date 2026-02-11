package com.shadowai.uicomposition

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Hosts and transitions between transformation views.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun TransformationViewHost(
    views: List<TransformationView>,
    executor: TransformationExecutor,
    modifier: Modifier = Modifier,
    stateStore: TransformationViewStateStore = rememberTransformationViewStateStore()
) {
    var activeViewType by remember { mutableStateOf(views.firstOrNull()?.viewType ?: ViewType.CHAT) }
    val activeView = views.firstOrNull { it.viewType == activeViewType } ?: return

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = views.indexOf(activeView)) {
            views.forEachIndexed { index, view ->
                Tab(
                    selected = index == views.indexOf(activeView),
                    onClick = { activeViewType = view.viewType },
                    text = {
                        Text(
                            text = view.title,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                )
            }
        }

        val state = stateStore.getOrCreate(activeView.viewType, activeView.parameterSchema)

        AnimatedContent(
            targetState = activeView,
            transitionSpec = {
                (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
            },
            label = "TransformationViewTransition"
        ) { view ->
            view.Render(
                state = state,
                executor = executor,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            )
        }
    }
}
