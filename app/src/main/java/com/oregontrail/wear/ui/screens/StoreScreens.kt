package com.oregontrail.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.oregontrail.wear.core.GameState
import com.oregontrail.wear.core.Good
import com.oregontrail.wear.ui.GameController
import com.oregontrail.wear.ui.Screen
import com.oregontrail.wear.ui.art.ArtNames
import com.oregontrail.wear.ui.components.Gap
import com.oregontrail.wear.ui.components.MenuChip
import com.oregontrail.wear.ui.components.SceneScrollColumn
import com.oregontrail.wear.ui.components.ScreenText
import com.oregontrail.wear.ui.components.ScreenTitle
import com.oregontrail.wear.ui.money
import com.oregontrail.wear.ui.purchaseCeiling
import com.oregontrail.wear.ui.purchaseStep
import com.oregontrail.wear.ui.shortUnit
import com.oregontrail.wear.ui.theme.AppleII
import com.oregontrail.wear.ui.theme.AppleIIChrome

@Composable
fun StoreScreen(controller: GameController) {
    val state = controller.game
    val store = controller.store ?: return
    val enoughOxen = state.inventory.oxen >= GameState.MINIMUM_OXEN

    SceneScrollColumn(background = ArtNames.STORE_INTERIOR) {
        Gap(4)
        ScreenTitle(store.name)
        ScreenText("You have ${money(state.inventory.cashCents)}", color = AppleII.Green)
        Gap(8)
        for (good in Good.entries) {
            MenuChip(
                label = good.displayName,
                // Price and holding on their own lines. Run together on one line they
                // came to 306 pixels for food ("$0.20/lb · have 200 lb") against the 204
                // a chip label gets on the physical watch, and the tail was silently cut
                // off mid-character — see [MenuChip]. Split, the longest either line
                // reaches is 178.
                secondaryLabel = "${money(store.priceCents(good))}/${good.shortUnit}\n" +
                    "have ${owned(controller, good)}",
                iconArt = ArtNames.good(good),
                onClick = { controller.go(Screen.StoreItem(good)) },
            )
            Gap(6)
        }
        MenuChip(
            label = if (enoughOxen) "Take to the trail" else "You need 2 oxen",
            primary = true,
            enabled = enoughOxen,
            onClick = { controller.depart() },
        )
    }
}

/** How much crown travel advances the quantity by one step. */
private const val ROTARY_PIXELS_PER_STEP = 40f

/**
 * Buying a quantity of one good.
 *
 * Hand-built rather than using Wear's [androidx.wear.compose.material.Stepper], which
 * reserves its top and bottom thirds for the two buttons and leaves a content area too
 * short for a quantity, a price *and* a confirm button — the button fell off the bottom
 * of the screen with no clipping warning of any kind.
 *
 * Driving the quantity from the crown is also the better fit for ADR 0004: the crown is
 * meant to be the primary control, and here it adjusts the number directly while the
 * hand stays off a screen that is mostly covered by a finger during a tap. The two
 * buttons remain for people who would rather tap.
 *
 * The running cost is shown rather than the unit price, because the decision being made
 * is "can I afford this and still buy oxen", not "what does a pound of food cost".
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun StoreItemScreen(controller: GameController, good: Good) {
    val state = controller.game
    val store = controller.store ?: return

    val step = good.purchaseStep
    val affordable = store.affordable(good, state.inventory.cashCents)
    val maximum = maxOf(minOf(affordable, good.purchaseCeiling).let { it - it % step }, 0)

    var quantity by remember(good) { mutableIntStateOf(0) }
    var carriedScroll by remember(good) { mutableFloatStateOf(0f) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(good) { focusRequester.requestFocus() }

    fun adjust(by: Int) {
        quantity = (quantity + by).coerceIn(0, maximum)
    }

    val cost = store.costCents(good, quantity)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleII.Black)
            .onRotaryScrollEvent { event ->
                // Accumulated rather than applied per event: a single detent delivers
                // several small events, and stepping on each one would run the quantity
                // away far faster than the crown is actually turning.
                carriedScroll += event.verticalScrollPixels
                val steps = (carriedScroll / ROTARY_PIXELS_PER_STEP).toInt()
                if (steps != 0) {
                    carriedScroll -= steps * ROTARY_PIXELS_PER_STEP
                    adjust(steps * step)
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${good.displayName} · ${good.purchaseUnit}",
                color = AppleIIChrome.MutedGreen,
                style = MaterialTheme.typography.caption1,
                textAlign = TextAlign.Center,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { adjust(-step) },
                    enabled = quantity > 0,
                    colors = ButtonDefaults.secondaryButtonColors(),
                ) {
                    Text("−", style = MaterialTheme.typography.title2)
                }
                Text(
                    text = "$quantity",
                    color = AppleII.Green,
                    style = MaterialTheme.typography.display3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(96.dp),
                )
                Button(
                    onClick = { adjust(step) },
                    enabled = quantity < maximum,
                    colors = ButtonDefaults.secondaryButtonColors(),
                ) {
                    Text("+", style = MaterialTheme.typography.title2)
                }
            }
            Text(
                text = if (maximum == 0) "You cannot afford any" else money(cost),
                color = AppleII.White,
                style = MaterialTheme.typography.caption1,
            )
            Gap(6)
            CompactChip(
                // At zero there is nothing to buy, so the same control is the way out.
                onClick = {
                    if (quantity == 0) {
                        controller.go(Screen.Store)
                    } else {
                        controller.buy(good, quantity)
                    }
                },
                label = { Text(if (quantity == 0) "Back" else "Buy") },
            )
        }
    }
}

/** How much of [good] the wagon already carries, in that good's purchase units. */
private fun owned(controller: GameController, good: Good): String {
    val inventory = controller.game.inventory
    return when (good) {
        Good.OXEN -> "${inventory.oxen}"
        Good.FOOD -> "${inventory.foodPounds} lb"
        Good.CLOTHING -> "${inventory.clothingSets}"
        Good.BULLETS -> "${inventory.bullets}"
        Good.SPARE_WHEEL -> "${inventory.spareWheels}"
        Good.SPARE_AXLE -> "${inventory.spareAxles}"
        Good.SPARE_TONGUE -> "${inventory.spareTongues}"
    }
}
