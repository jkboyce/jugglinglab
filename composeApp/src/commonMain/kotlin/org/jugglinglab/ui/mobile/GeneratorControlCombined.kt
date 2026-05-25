//
// GeneratorControlCombined.kt
//
// Combined view for the Siteswap Generator and Transitioner.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.mobile

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.ui.common.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource

@Composable
fun GeneratorControlCombined(
    isBusy: Boolean,
    onGeneratorConfirm: (String) -> Unit,
    onTransitionerConfirm: (String) -> Unit,
    generatorState: SiteswapGeneratorState = remember { SiteswapGeneratorState() },
    transitionerState: SiteswapTransitionerState = remember { SiteswapTransitionerState() },
    combinedState: GeneratorControlCombinedState = remember { GeneratorControlCombinedState() }
) {
    var selectedTabIndex by combinedState.selectedTabIndex

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { Text(stringResource(Res.string.gui_generator), fontSize = 16.sp) }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { Text(stringResource(Res.string.gui_transitions), fontSize = 16.sp) }
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTabIndex) {
                0 -> SiteswapGeneratorControl(onConfirm = onGeneratorConfirm, isBusy = isBusy, state = generatorState)
                1 -> SiteswapTransitionerControl(onConfirm = onTransitionerConfirm, isBusy = isBusy, state = transitionerState)
            }
        }
    }
}

class GeneratorControlCombinedState {
    val selectedTabIndex = mutableIntStateOf(0)
}
