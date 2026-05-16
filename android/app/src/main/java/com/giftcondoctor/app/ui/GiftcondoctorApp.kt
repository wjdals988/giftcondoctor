package com.giftcondoctor.app.ui

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.navArgument
import com.giftcondoctor.app.ui.screens.AddCouponScreen
import com.giftcondoctor.app.ui.screens.CouponDetailScreen
import com.giftcondoctor.app.ui.screens.CreateRoomScreen
import com.giftcondoctor.app.ui.screens.JoinRoomScreen
import com.giftcondoctor.app.ui.screens.LoginScreen
import com.giftcondoctor.app.ui.screens.MemberListScreen
import com.giftcondoctor.app.ui.screens.NotificationSettingsScreen
import com.giftcondoctor.app.ui.screens.RoomDetailScreen
import com.giftcondoctor.app.ui.screens.RoomListScreen
import com.giftcondoctor.app.ui.screens.RoomSettingsScreen
import com.giftcondoctor.app.ui.components.RequestNotificationPermissionOnLaunch
import com.giftcondoctor.app.ui.viewmodel.SessionViewModel

object Routes {
    const val Login = "login"
    const val Rooms = "rooms"
    const val CreateRoom = "rooms/create"
    const val JoinRoom = "rooms/join"
    const val Notifications = "settings/notifications"
    const val RoomDetail = "rooms/{roomId}"
    const val AddCoupon = "rooms/{roomId}/coupons/add"
    const val CouponDetail = "rooms/{roomId}/coupons/{couponId}"
    const val RoomSettings = "rooms/{roomId}/settings"
    const val Members = "rooms/{roomId}/members"
}

@Composable
fun GiftcondoctorApp(sessionViewModel: SessionViewModel = viewModel()) {
    val navController = rememberNavController()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val loggedIn by sessionViewModel.isLoggedIn.collectAsStateWithLifecycle()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    LaunchedEffect(loggedIn, currentRoute) {
        if (loggedIn && currentRoute == Routes.Login) {
            navController.navigate(Routes.Rooms) {
                popUpTo(Routes.Login) { inclusive = true }
                launchSingleTop = true
            }
        } else if (!loggedIn && currentRoute != null && currentRoute != Routes.Login) {
            navController.navigate(Routes.Login) {
                popUpTo(Routes.Rooms) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme()
    ) {
        RequestNotificationPermissionOnLaunch()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitFirstDown(pass = PointerEventPass.Initial)
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    }
                }
        ) {
            NavHost(navController = navController, startDestination = Routes.Login) {
                composable(Routes.Login) {
                    LoginScreen(sessionViewModel)
                }
                composable(Routes.Rooms) {
                    RoomListScreen(
                        sessionViewModel = sessionViewModel,
                        onOpenRoom = { navController.navigate("rooms/$it") },
                        onCreateRoom = { navController.navigate(Routes.CreateRoom) },
                        onJoinRoom = { navController.navigate(Routes.JoinRoom) },
                        onOpenNotifications = { navController.navigate(Routes.Notifications) }
                    )
                }
                composable(Routes.CreateRoom) {
                    CreateRoomScreen(
                        onBack = { navController.popBackStack() },
                        onCreated = { navController.navigate("rooms/$it") { popUpTo(Routes.Rooms) } }
                    )
                }
                composable(Routes.JoinRoom) {
                    JoinRoomScreen(
                        onBack = { navController.popBackStack() },
                        onJoined = { navController.navigate("rooms/$it") { popUpTo(Routes.Rooms) } }
                    )
                }
                composable(Routes.Notifications) {
                    NotificationSettingsScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    route = Routes.RoomDetail,
                    arguments = listOf(navArgument("roomId") { type = NavType.StringType })
                ) {
                    val roomId = it.arguments?.getString("roomId").orEmpty()
                    RoomDetailScreen(
                        roomId = roomId,
                        onBack = { navController.popBackStack() },
                        onAddCoupon = { navController.navigate("rooms/$roomId/coupons/add") },
                        onOpenCoupon = { couponId -> navController.navigate("rooms/$roomId/coupons/$couponId") },
                        onOpenMembers = { navController.navigate("rooms/$roomId/members") },
                        onOpenSettings = { navController.navigate("rooms/$roomId/settings") }
                    )
                }
                composable(
                    route = Routes.AddCoupon,
                    arguments = listOf(navArgument("roomId") { type = NavType.StringType })
                ) {
                    val roomId = it.arguments?.getString("roomId").orEmpty()
                    AddCouponScreen(
                        roomId = roomId,
                        onBack = { navController.popBackStack() },
                        onAdded = { couponId ->
                            navController.navigate("rooms/$roomId/coupons/$couponId") {
                                popUpTo("rooms/$roomId")
                            }
                        }
                    )
                }
                composable(
                    route = Routes.CouponDetail,
                    arguments = listOf(
                        navArgument("roomId") { type = NavType.StringType },
                        navArgument("couponId") { type = NavType.StringType }
                    ),
                    deepLinks = listOf(navDeepLink { uriPattern = "giftcondoctor://rooms/{roomId}/coupons/{couponId}" })
                ) {
                    val roomId = it.arguments?.getString("roomId").orEmpty()
                    val couponId = it.arguments?.getString("couponId").orEmpty()
                    CouponDetailScreen(
                        roomId = roomId,
                        couponId = couponId,
                        onBack = { navController.popBackStack() },
                        onDeleted = { navController.popBackStack("rooms/$roomId", false) }
                    )
                }
                composable(
                    route = Routes.RoomSettings,
                    arguments = listOf(navArgument("roomId") { type = NavType.StringType })
                ) {
                    val roomId = it.arguments?.getString("roomId").orEmpty()
                    RoomSettingsScreen(
                        roomId = roomId,
                        onBack = { navController.popBackStack() },
                        onLeft = { navController.navigate(Routes.Rooms) { popUpTo(Routes.Rooms) { inclusive = true } } }
                    )
                }
                composable(
                    route = Routes.Members,
                    arguments = listOf(navArgument("roomId") { type = NavType.StringType })
                ) {
                    val roomId = it.arguments?.getString("roomId").orEmpty()
                    MemberListScreen(
                        roomId = roomId,
                        currentUid = sessionViewModel.currentUid,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
