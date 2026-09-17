package com.mati.shortformblocker.detect

/**
 * Publishes the live feed-budget state from the accessibility service to the UI.
 *
 * The budget is spent inside [FeedBudgetPolicy], which lives in the service and cannot be asked
 * questions from a composable, so the service pushes a copy here after every screen it evaluates
 * and the home screen reads it on its once-a-second tick. Same process, so this is a plain volatile
 * field rather than anything with a channel in it.
 *
 * Nothing is written to disk. The state is cleared when the service goes away, because
 * [FeedBudgetPolicy.reset] goes with it: a countdown for a budget nothing is enforcing any more
 * would be the one kind of wrong that matters here, telling you to wait when you need not.
 */
object FeedBudgetHolder {

    @Volatile
    var states: Map<String, FeedVisitState> = emptyMap()
        private set

    fun record(states: Map<String, FeedVisitState>) {
        this.states = states
    }

    fun stateFor(packages: Set<String>): FeedVisitState? = states.feedStateFor(packages)

    fun clear() {
        states = emptyMap()
    }
}

/**
 * The visit for a rule, which may cover several packages - Facebook ships as two apps, and so does
 * Instagram. The most recently active one wins: they share a budget only in the sense that you can
 * only be in one of them at a time.
 */
fun Map<String, FeedVisitState>.feedStateFor(packages: Set<String>): FeedVisitState? =
    packages.mapNotNull { this[it] }.maxByOrNull { it.refillsAt }
