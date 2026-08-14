package com.oregontrail.wear.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.oregontrail.wear.core.Animal
import com.oregontrail.wear.core.CrossingMethod
import com.oregontrail.wear.core.CrossingResult
import com.oregontrail.wear.core.Encounter
import com.oregontrail.wear.core.Encounters
import com.oregontrail.wear.core.GameEvent
import com.oregontrail.wear.core.GameState
import com.oregontrail.wear.core.Good
import com.oregontrail.wear.core.Hunting
import com.oregontrail.wear.core.HuntOutcome
import com.oregontrail.wear.core.HuntingGround
import com.oregontrail.wear.core.LandmarkId
import com.oregontrail.wear.core.Pace
import com.oregontrail.wear.core.PartyNames
import com.oregontrail.wear.core.Profession
import com.oregontrail.wear.core.Rations
import com.oregontrail.wear.core.Rng
import com.oregontrail.wear.core.RiverConditions
import com.oregontrail.wear.core.RiverCrossing
import com.oregontrail.wear.core.Store
import com.oregontrail.wear.core.TurnEngine
import com.oregontrail.wear.data.SaveRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Holds the run and everything the screens need to know about it.
 *
 * Deliberately *not* an Android `ViewModel`. A watch does not rotate, this app has one
 * activity, and every mutation is written straight to disk anyway — so the one thing a
 * ViewModel would buy us (surviving configuration change) is already covered by the save
 * file, and using one would mean adding a dependency for nothing.
 *
 * All *state* mutation happens on the main thread from Compose callbacks. That is safe
 * because the engine is pure and each call is a cheap synchronous transform of an
 * immutable [GameState]. The disk write that follows it is not cheap and does not happen
 * there — see [commit].
 */
class GameController(
    private val repository: SaveRepository,
    /**
     * Where saves are written. Injected so the JVM tests can pass a scope they control
     * and await, rather than racing a background write.
     */
    saveScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    /**
     * What the save worker has been asked to do next: write a state, or delete the file.
     *
     * Both go through one queue so they cannot reorder. Abandoning a run used to delete
     * the file inline, which — now that writes are asynchronous — would let a write
     * queued a moment earlier land *after* the delete and resurrect the run the player
     * just abandoned.
     */
    private sealed interface SaveTask {
        data class Write(val state: GameState) : SaveTask
        data object Clear : SaveTask
    }

    /**
     * The task waiting to be performed, and the worker draining it.
     *
     * A conflated channel rather than a job per save: travel advances a day every 700ms
     * and each of those days is several mutations, so saves are queued faster than a
     * watch's flash retires them. Conflation collapses a backlog to the newest task,
     * which is the only one that matters — a state superseded before it reached the disk
     * was never worth writing.
     *
     * Off the main thread because the write is not cheap: it serialises the whole run to
     * JSON, writes a temp file, deletes, and renames. Doing that inline in [commit] put
     * four filesystem round-trips inside the frame that had to draw the result, which on
     * the trail is a frame every 700ms.
     */
    private val saves = Channel<SaveTask>(Channel.CONFLATED)

    init {
        saveScope.launch {
            for (task in saves) perform(task)
        }
    }

    /**
     * Locked because [flushSave] performs tasks from the main thread while the worker may
     * be performing one of its own. The queue hands each task to exactly one of them, so
     * they never duplicate work, but a write and a delete overlapping on the same file
     * would still be a race.
     */
    private val fileLock = Any()

    private fun perform(task: SaveTask) = synchronized(fileLock) {
        when (task) {
            is SaveTask.Write -> repository.save(task.state)
            SaveTask.Clear -> repository.clear()
        }
    }

    /**
     * Performs any queued save or delete immediately, on the calling thread.
     *
     * Called from `onStop` — the last moment the app is reliably alive, since Wear OS
     * kills backgrounded apps without warning and a queued write is only as durable as
     * the process holding it — and before anything reads the file back, so a read never
     * sees a state the queue has already superseded. Between this and conflation the disk
     * is at most one frame behind the screen while the app is visible, and exactly current
     * the moment it isn't.
     */
    fun flushSave() {
        while (true) {
            val task = saves.tryReceive().getOrNull() ?: return
            perform(task)
        }
    }

    var screen: Screen by mutableStateOf(Screen.Title)
        private set

    var state: GameState? by mutableStateOf(null)
        private set

    /**
     * Events waiting to be read, shown one at a time. Travel does not resume until this
     * is empty — see [EventText.pausesTravel] for what earns a place in it.
     */
    var pendingEvents: List<GameEvent> by mutableStateOf(emptyList())
        private set

    /** The latest event not worth stopping for, shown in passing. */
    var ticker: String? by mutableStateOf(null)
        private set

    /** How the last river crossing went, held so the result screen can show it. */
    var crossingResult: CrossingResult? by mutableStateOf(null)
        private set

    /**
     * Which method produced [crossingResult].
     *
     * The result alone doesn't say how the river was attempted, and the picture the
     * player should see does: a safe ferry and a safe ford are the same [CrossingResult]
     * but very different images.
     */
    var crossingMethod: CrossingMethod? by mutableStateOf(null)
        private set

    /** Distance covered on the most recent day, for the travel readout. */
    var lastDayMiles: Int by mutableStateOf(0)
        private set

    /**
     * Someone met on the road today, awaiting a decision.
     *
     * Not part of [state] and not saved: like [pendingEvents], losing an unresolved
     * encounter to a process death is the same as never having met them.
     */
    var pendingEncounter: Encounter? by mutableStateOf(null)
        private set

    private var chosenProfession: Profession? = null
    private var arrivedThisDay = false

    /**
     * The current hunt's RNG fork, held only while [Screen.Hunt] is on screen.
     *
     * Not part of [state]: a hunt in progress is unsaved, exactly like being midway
     * through any other menu, so losing it to a process death costs nothing but the
     * session itself. See [Hunting.start].
     */
    private var huntRng: Rng? = null

    // Flushed first so the answer accounts for a save queued but not yet written. Only
    // ever read on the title screen, where the queue is empty, so this costs nothing in
    // practice — but it makes the answer correct by construction rather than by luck.
    val hasSave: Boolean get() {
        flushSave()
        return repository.hasSave()
    }

    /** The run, or an error if a screen that requires one is somehow showing without it. */
    val game: GameState
        get() = state ?: error("no run in progress on screen $screen")

    // ---- Setup ----

    fun startNewGame() {
        chosenProfession = null
        screen = Screen.ChooseProfession
    }

    fun chooseProfession(profession: Profession) {
        chosenProfession = profession
        screen = Screen.ChooseMonth
    }

    fun chooseMonth(month: Int) {
        val profession = chosenProfession ?: return
        commit(
            GameState.newGame(
                profession = profession,
                memberNames = PartyNames.defaultParty,
                startMonth = month,
                // Seeded from the clock so each run differs, but stored inside the state
                // and serialised with it, so a resumed run continues the same sequence.
                seed = System.nanoTime(),
            )
        )
        screen = Screen.Store
    }

    /** Resumes the saved run, or falls back to the title screen if it cannot be read. */
    fun resumeSavedGame() {
        flushSave()
        val loaded = repository.load()
        if (loaded == null) {
            screen = Screen.Title
            return
        }
        state = loaded
        pendingEvents = emptyList()
        pendingEncounter = null
        ticker = null
        screen = resumeScreen(loaded)
    }

    fun abandonRun() {
        // Queued like every other file operation so it cannot overtake or be overtaken by
        // a pending write, then flushed so the title screen this returns to sees the run
        // actually gone. Rare and deliberate enough that a synchronous delete is free.
        saves.trySend(SaveTask.Clear)
        flushSave()
        state = null
        pendingEvents = emptyList()
        pendingEncounter = null
        ticker = null
        crossingResult = null
        crossingMethod = null
        screen = Screen.Title
    }

    // ---- Navigation ----

    fun go(destination: Screen) {
        screen = destination
    }

    /** Leaves the store and takes to the trail. */
    fun depart() {
        screen = resumeScreen(game)
    }

    // ---- The store ----

    val store: Store? get() = state?.store

    fun buy(good: Good, quantity: Int) {
        val current = game
        val shop = current.store ?: return
        if (quantity <= 0) return
        if (shop.costCents(good, quantity) > current.inventory.cashCents) return
        commit(current.copy(inventory = shop.buy(current.inventory, good, quantity)))
        screen = Screen.Store
    }

    // ---- The trail ----

    /**
     * True when the wagon physically cannot move, whatever the player does.
     *
     * Worth a dedicated flag rather than leaving it implicit: without one the travel
     * loop would keep ticking days that cover zero miles, quietly starving the party
     * while appearing to work. The player is told instead.
     */
    val cannotMove: Boolean
        get() = state?.let { it.inventory.oxen < GameState.MINIMUM_OXEN } ?: false

    /** True while something is waiting to be read and travel should hold. */
    val isPaused: Boolean get() = pendingEvents.isNotEmpty() || pendingEncounter != null

    fun advanceDay() {
        val current = state ?: return
        if (current.isOver || current.awaitingDecision || isPaused) return

        val result = TurnEngine.advanceDay(current)
        commit(result.state)
        absorb(result.events)
        pendingEncounter = result.encounter
    }

    fun restOneDay() {
        val current = state ?: return
        if (current.isOver) return
        val result = TurnEngine.rest(current)
        commit(result.state)
        absorb(result.events)
        if (!isPaused) screen = resumeScreen(result.state)
    }

    fun setPace(pace: Pace) {
        commit(game.copy(pace = pace))
        screen = Screen.TrailMenu
    }

    fun setRations(rations: Rations) {
        commit(game.copy(rations = rations))
        screen = Screen.TrailMenu
    }

    /** Acknowledges the event on screen and moves to whatever comes next. */
    fun dismissEvent() {
        if (pendingEvents.isEmpty()) return
        pendingEvents = pendingEvents.drop(1)
        if (pendingEvents.isNotEmpty()) return

        val current = game
        screen = when {
            current.isOver -> Screen.GameOver
            // The landmark screen comes first so the player sees where they are before
            // being asked to decide anything about it.
            arrivedThisDay -> Screen.Arrival
            else -> resumeScreen(current)
        }
        arrivedThisDay = false
    }

    // ---- Rivers and branches ----

    val riverConditions: RiverConditions?
        get() = state?.let { RiverCrossing.conditionsAt(it) }

    fun cross(method: CrossingMethod) {
        val current = game
        val conditions = RiverCrossing.conditionsAt(current) ?: return
        if (method !in conditions.available(current.inventory.cashCents)) return

        val outcome = RiverCrossing.cross(current, conditions, method)
        commit(outcome.state)
        crossingResult = outcome.result
        crossingMethod = method
    }

    /** Acknowledges the crossing result and carries on. */
    fun dismissCrossing() {
        crossingResult = null
        crossingMethod = null
        val current = game
        screen = if (current.isOver) Screen.GameOver else resumeScreen(current)
    }

    fun chooseRoute(to: LandmarkId) {
        commit(TurnEngine.chooseRoute(game, to))
        screen = resumeScreen(game)
    }

    // ---- Encounters ----

    /** Waves off a traveller, or declines a trade or a guide's offer. */
    fun dismissEncounter() {
        pendingEncounter = null
    }

    fun acceptTrade() {
        val trade = pendingEncounter as? Encounter.Trade ?: return
        commit(Encounters.accept(game, trade))
        pendingEncounter = null
    }

    fun hireGuide() {
        val guide = pendingEncounter as? Encounter.Guide ?: return
        commit(Encounters.hire(game, guide))
        pendingEncounter = null
    }

    // ---- Hunting ----

    val canHunt: Boolean get() = state?.let { Hunting.canHunt(it) } ?: false

    val huntGround: HuntingGround? get() = state?.let { Hunting.groundAt(it) }

    fun startHunt() {
        huntRng = Hunting.start(game)
        screen = Screen.Hunt
    }

    /** Rolls the meat one hit on [animal] yields, for the hunt screen to display. */
    fun huntMeatRoll(animal: Animal): Int = Hunting.meatFor(huntRng ?: game.rng, animal)

    /**
     * Books a finished hunt and returns to the trail. Called once, when the player
     * leaves the hunt screen — whether by running out of ammunition, waiting out the
     * session, or tapping away.
     */
    fun finishHunt(bulletsUsed: Int, meatPoundsShot: Int, bearsEscaped: Int) {
        val rng = huntRng ?: game.rng.fork()
        huntRng = null
        val outcome = Hunting.finish(game, rng, bulletsUsed, meatPoundsShot, bearsEscaped)
        commit(outcome.day.state)
        absorb(outcome.day.events)
        ticker = huntSummary(outcome)
        if (!isPaused) screen = resumeScreen(outcome.day.state)
    }

    private fun huntSummary(outcome: HuntOutcome): String = buildString {
        if (outcome.meatPoundsCarried > 0) {
            append("Brought back ${outcome.meatPoundsCarried} lb of meat")
            if (outcome.meatPoundsWasted > 0) append(" (${outcome.meatPoundsWasted} lb left behind)")
        } else {
            append("Came back empty-handed")
        }
        if (outcome.bearsEscaped > 0) append(". A bear charged before you got clear")
        append(".")
    }

    // ---- Debug ----

    /**
     * Debug-only: replaces the run with [next] and jumps straight to [destination],
     * bypassing the normal game loop. Used by [Screen.Debug] so a screen's art/layout
     * can be checked without playing to it. Clears anything that would otherwise take
     * priority over [destination] in [com.oregontrail.wear.ui.OregonTrailApp] — an
     * event or encounter left over from whatever the player was doing before opening
     * the debug menu, say.
     */
    fun debugJumpTo(next: GameState, destination: Screen) {
        commit(next)
        pendingEvents = emptyList()
        pendingEncounter = null
        crossingResult = null
        crossingMethod = null
        ticker = null
        screen = destination
    }

    // ---- Internals ----

    /**
     * Records a new state and queues it to disk.
     *
     * Every mutation saves. A run is 20-30 minutes on a device that kills backgrounded
     * apps without warning, so anything less than save-on-every-change means losing
     * progress. The write itself happens on [saves]' worker rather than here — see that
     * channel and [flushSave] for why the guarantee survives the move.
     */
    private fun commit(next: GameState) {
        state = next
        saves.trySend(SaveTask.Write(next))
    }

    /** Sorts a day's events into what stops the wagon and what merely scrolls past. */
    private fun absorb(events: List<GameEvent>) {
        lastDayMiles = events.filterIsInstance<GameEvent.Traveled>().sumOf { it.miles }
        arrivedThisDay = events.any { it is GameEvent.ArrivedAt }

        pendingEvents = events.filter { EventText.pausesTravel(it) }
        ticker = events
            .filterNot { EventText.pausesTravel(it) }
            .mapNotNull { EventText.describe(it) }
            .lastOrNull()

        // A run can end without any event that pauses — the last traveller dying of an
        // illness resolved days ago, for instance. Route to the ending regardless.
        val current = state
        if (pendingEvents.isEmpty() && current != null && current.isOver) {
            screen = Screen.GameOver
        }
    }
}
