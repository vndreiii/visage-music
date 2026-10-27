package com.music.vivi.compose.ui.onboarding

import com.music.vivi.compose.prefs.LibrarySidebarState
import kotlinx.coroutines.flow.MutableSharedFlow

/** Things the tour makes the real app do, so each step is shown live rather than described. */
sealed interface TourCommand {
    data class Navigate(val route: String) : TourCommand
    /** Opens or closes the queue sidebar without touching the user's saved queue mode. */
    data class Queue(val open: Boolean) : TourCommand
    data class Library(val state: LibrarySidebarState) : TourCommand
}

object TourBus {
    val commands = MutableSharedFlow<TourCommand>(extraBufferCapacity = 16)
    fun send(command: TourCommand) { commands.tryEmit(command) }
}
