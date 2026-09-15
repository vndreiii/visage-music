@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.music.vivi.compose.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import kotlin.test.*

class DesktopNavigationTest {
    @Test fun homeLibraryRoundTripsAndNestedPagesKeepTabsReachable() = runBlocking(Dispatchers.Swing) {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        val store = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        lateinit var nav: NavHostController
        val scene = ImageComposeScene(width = 800, height = 600, coroutineContext = coroutineContext) {
            CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalViewModelStoreOwner provides store) {
                nav = rememberNavController()
                MaterialTheme {
                    NavHost(navController = nav, startDestination = Routes.HOME) {
                        composable(Routes.HOME) { Text("Home") }
                        composable(Routes.LIBRARY) { Text("Library") }
                        composable(Routes.SEARCH) { Text("Search") }
                        composable("detail") { Text("Album") }
                    }
                }
            }
        }
        try {
            var clock = 0L
            fun render() { clock += 1_000_000_000; scene.render(clock).close() }
            render()
            repeat(4) {
                navigateMain(nav, Routes.LIBRARY); render()
                assertEquals(Routes.LIBRARY, nav.currentDestination?.route)
                navigateMain(nav, Routes.HOME); render()
                assertEquals(Routes.HOME, nav.currentDestination?.route)
            }
            navigateMain(nav, Routes.LIBRARY); render()
            nav.navigate("detail"); render()
            navigateMain(nav, Routes.HOME); render()
            navigateMain(nav, Routes.LIBRARY); render()
            // A saved tab may restore its nested page; popping it must expose Library.
            if (nav.currentDestination?.route == "detail") { nav.popBackStack(); render() }
            assertEquals(Routes.LIBRARY, nav.currentDestination?.route)
            navigateMain(nav, Routes.SEARCH); render()
            navigateMain(nav, Routes.LIBRARY); render()
            assertEquals(Routes.LIBRARY, nav.currentDestination?.route)
        } finally {
            scene.close()
            store.viewModelStore.clear()
            owner.lifecycle.currentState = Lifecycle.State.DESTROYED
        }
    }
}
