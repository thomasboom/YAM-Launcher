package eu.ottop.yamlauncher.compose

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import eu.ottop.yamlauncher.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun HomeScreen(
    vm: LauncherViewModel,
    onOpenSettings: () -> Unit,
) {
    val p by vm.uiPrefs.collectAsState()
    val context = LocalContext.current

    val verticalOffset = remember { mutableFloatStateOf(0f) }
    val horizontalOffset = remember { mutableFloatStateOf(0f) }

    fun flingUp() = vm.openDrawerFromFling()
    fun flingDown() = vm.expandNotifications()
    fun flingLeft() {
        if (p.gesturesEnabled["left"] == true) {
            val (activity, profile) = vm.gestureTarget("left")
            if (activity != null && profile != null && profile in vm.launcherApps.profiles.indices) {
                vm.launchGestureTarget(activity to profile, "left") {}
            } else {
                Toast.makeText(context, context.getString(R.string.launch_error), Toast.LENGTH_SHORT).show()
            }
        }
    }
    fun flingRight() {
        if (p.gesturesEnabled["right"] == true) {
            val (activity, profile) = vm.gestureTarget("right")
            if (activity != null && profile != null && profile in vm.launcherApps.profiles.indices) {
                vm.launchGestureTarget(activity to profile, "right") {}
            } else {
                Toast.makeText(context, context.getString(R.string.launch_error), Toast.LENGTH_SHORT).show()
            }
        }
    }
    fun doubleTap() {
        if (!p.doubleTapEnabled) return
        if (p.doubleTapAction == "app") {
            val (activity, profile) = vm.gestureTarget("doubleTap")
            if (activity != null && profile != null && profile in vm.launcherApps.profiles.indices) {
                vm.launchGestureTarget(activity to profile, "doubleTap", requireEnabled = false) {}
            } else {
                Toast.makeText(context, context.getString(R.string.launch_error), Toast.LENGTH_SHORT).show()
            }
        } else {
            if (vm.accessibilityServiceEnabled()) vm.lockScreen()
            else vm.promptAccessibility()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(p.swipeThreshold, p.swipeVelocity) {
                detectTapGestures(
                    onDoubleTap = { doubleTap() },
                    onLongPress = { onOpenSettings() },
                )
            }
            .draggable(
                state = rememberDraggableState { verticalOffset.floatValue += it },
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    val offset = verticalOffset.floatValue
                    verticalOffset.floatValue = 0f
                    if (offset < -p.swipeThreshold && velocity < -p.swipeVelocity) flingUp()
                    else if (offset > p.swipeThreshold && velocity > p.swipeVelocity) flingDown()
                },
            )
            .draggable(
                state = rememberDraggableState { horizontalOffset.floatValue += it },
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity ->
                    val offset = horizontalOffset.floatValue
                    horizontalOffset.floatValue = 0f
                    if (offset < 0 && -offset > p.swipeThreshold && velocity < -p.swipeVelocity) flingLeft()
                    else if (offset > 0 && offset > p.swipeThreshold && velocity > p.swipeVelocity) flingRight()
                },
            )
            .semantics(mergeDescendants = false) {},
    ) {
        // Homescreen darkening overlay (behind content, over wallpaper)
        if (p.homescreenDarkening) {
            Spacer(modifier = Modifier.matchParentSize().background(Color(0x3F000000)))
        }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            if (landscape) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxSize().padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        DefaultLauncherBanner(vm)
                        ClockBlock(vm, onOpenSettings)
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                        ShortcutList(vm, compact = true, onOpenSettings = onOpenSettings)
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    DefaultLauncherBanner(vm)
                    ClockBlock(vm, onOpenSettings)
                    ShortcutList(vm, compact = false, onOpenSettings = onOpenSettings)
                }
            }
        }
    }
}

@Composable
private fun DefaultLauncherBanner(vm: LauncherViewModel) {
    val isDefault by vm.isDefaultLauncher.collectAsState()
    if (isDefault) return
    val p by vm.uiPrefs.collectAsState()
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.set_default_launcher_prompt),
            modifier = Modifier.weight(1f),
            style = launcherTextStyle(p, 12.sp),
            maxLines = 3,
        )
        Text(
            text = stringResource(R.string.set_default_launcher_button),
            modifier = Modifier
                .padding(start = 8.dp)
                .combinedClickable(onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, context.getString(R.string.unable_to_launch_settings), Toast.LENGTH_SHORT).show()
                    }
                }, onLongClick = {}),
            style = launcherTextStyle(p, 12.sp).copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = Color(0xFF7F80CBC4),
            ),
        )
    }
}

@Composable
private fun ClockBlock(vm: LauncherViewModel, onOpenSettings: () -> Unit) {
    val p by vm.uiPrefs.collectAsState()
    val context = LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(60_000 - System.currentTimeMillis() % 60_000 + 500)
            now = System.currentTimeMillis()
        }
    }
    val use24h = DateFormat.is24HourFormat(context)
    val timeText = remember(now, use24h) {
        SimpleDateFormat(if (use24h) "HH:mm" else "hh:mm", Locale.getDefault()).format(Date(now))
    }
    val dateBase = remember(now) {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(now))
    }
    val weather by vm.weatherText.collectAsState()
    val battery by vm.batteryText.collectAsState()
    val screenTime by vm.screenTimeText.collectAsState()

    val align = alignmentFor(p.clockAlign)
    val clockModifier = Modifier
        .fillMaxWidth()
        .padding(start = 32.dp, end = 32.dp, top = 45.dp, bottom = 8.dp)
        .then(
            if (p.clockClick) {
                Modifier.combinedClickable(
                    onClick = {
                        vm.launchGestureTarget(vm.gestureTarget("clock"), "clock") {
                            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
                            if (intent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(intent)
                            }
                        }
                    },
                    onLongClick = onOpenSettings,
                )
            } else {
                Modifier.combinedClickable(onClick = {}, onLongClick = onOpenSettings)
            },
        )

    if (p.clockEnabled) {
        Text(
            text = timeText,
            modifier = clockModifier.semantics { contentDescription = timeText },
            style = launcherTextStyle(p, p.clockSizeSp.sp, align),
            maxLines = 1,
        )
    }
    if (p.dateEnabled) {
        DateLine(
            vm = vm,
            dateBase = dateBase,
            weather = weather,
            battery = battery,
            screenTime = screenTime,
        )
    }
}

@Composable
private fun DateLine(
    vm: LauncherViewModel,
    dateBase: String,
    weather: String,
    battery: String,
    screenTime: String,
) {
    val p by vm.uiPrefs.collectAsState()
    val context = LocalContext.current
    val annotated: AnnotatedString = remember(dateBase, weather, battery, screenTime) {
        buildAnnotatedString {
            append(dateBase)
            if (weather.isNotEmpty()) {
                append(" | ")
                pushStringAnnotation("section", "weather")
                append(weather)
                pop()
            }
            if (battery.isNotEmpty()) {
                append(" | ")
                pushStringAnnotation("section", "battery")
                append(battery)
                pop()
            }
            if (screenTime.isNotEmpty()) {
                append(" | ")
                append(screenTime)
            }
        }
    }
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    BasicText(
        text = annotated,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .pointerInput(annotated) {
                detectTapGestures { pos ->
                    val lr = layoutResult ?: return@detectTapGestures
                    val offset = lr.getOffsetForPosition(pos)
                    val annotation = annotated.getStringAnnotations(offset, offset).firstOrNull()
                    when (annotation?.item) {
                        "weather" -> vm.launchGestureTarget(
                            vm.gestureTarget("weather"), "weather", requireEnabled = false
                        ) {
                            val intent = Intent(
                                Intent.makeMainSelectorActivity(
                                    Intent.ACTION_MAIN, Intent.CATEGORY_APP_WEATHER
                                )
                            )
                            if (intent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(intent)
                            } else {
                                Toast.makeText(context, context.getString(R.string.no_weather_app), Toast.LENGTH_SHORT).show()
                            }
                        }
                        "battery" -> try {
                            context.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, context.getString(R.string.unable_to_launch_settings), Toast.LENGTH_SHORT).show()
                        }
                        else -> if (p.dateClick) {
                            vm.launchGestureTarget(vm.gestureTarget("date"), "date") {
                                try {
                                    context.startActivity(
                                        Intent(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
                                    )
                                } catch (e: ActivityNotFoundException) {
                                    Toast.makeText(context, context.getString(R.string.no_calendar_app), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            },
        style = launcherTextStyle(p, p.dateSizeSp.sp, alignmentFor(p.clockAlign)),
        onTextLayout = { layoutResult = it },
        maxLines = 4,
    )
}

@Composable
private fun ColumnScope.ShortcutList(
    vm: LauncherViewModel,
    compact: Boolean,
    onOpenSettings: () -> Unit,
) {
    val p by vm.uiPrefs.collectAsState()
    val context = LocalContext.current
    val notifPackages by vm.notifPackages.collectAsState()

    val (topWeight, bottomWeight) = when (p.shortcutVAlign) {
        "top" -> 0.1f to 0.42f
        "bottom" -> 0.42f to 0.1f
        else -> 0.22f to 0.3f
    }
    Spacer(modifier = Modifier.weight(topWeight))
    for (i in 0 until p.shortcutCount.coerceIn(0, 15)) {
        val setting = remember(p, i) { vm.shortcutAt(i) }
        val label = setting?.label?.ifBlank { stringResource(R.string.shortcut_default) }
            ?: stringResource(R.string.shortcut_default)
        val isWork = setting != null && !setting.isContact && setting.profile != 0
        val hasDot = p.dotsEnabled && setting != null && !setting.isContact &&
            setting.componentName != "e" &&
            notifPackages.contains(setting.componentName.substringBefore("/"))
        val style = launcherTextStyle(p, p.shortcutSizeSp.sp, alignmentFor(p.shortcutAlign))
        val rowModifier = Modifier
            .weight(p.shortcutWeight.coerceIn(0.01f, 1f))
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .combinedClickable(
                onClick = {
                    if (setting == null) {
                        Toast.makeText(context, context.getString(R.string.shortcut_default_click), Toast.LENGTH_SHORT).show()
                    } else if (setting.isContact) {
                        vm.onContactClick(setting.profile, context)
                    } else {
                        vm.launchShortcut(setting)
                    }
                },
                onLongClick = {
                    if (!p.shortcutsLocked) vm.beginShortcutPick(i)
                },
            )
        Row(
            modifier = rowModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val leadIcon = when {
                isWork -> R.drawable.ic_work_app
                else -> R.drawable.ic_empty
            }
            when (p.shortcutAlign) {
                "center" -> {
                    ShortcutIcon(leadIcon, p)
                    Text(
                        text = label, modifier = Modifier.weight(1f),
                        style = style, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    ShortcutIcon(leadIcon, p)
                }
                "right" -> {
                    Text(
                        text = label, modifier = Modifier.weight(1f),
                        style = style, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    ShortcutIcon(leadIcon, p)
                }
                else -> {
                    ShortcutIcon(leadIcon, p)
                    Text(
                        text = label, modifier = Modifier.weight(1f),
                        style = style, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (hasDot) {
                ShortcutIcon(R.drawable.notification_dot, p)
            }
        }
    }
    Spacer(modifier = Modifier.weight(bottomWeight))
}

@Composable
private fun ShortcutIcon(res: Int, p: UiPrefs) {
    // ic_empty is a pathless placeholder vector that Compose cannot parse;
    // notification_dot is a shape drawable Compose cannot load either.
    // Render both as pure Compose primitives instead.
    when (res) {
        R.drawable.ic_empty -> Spacer(
            modifier = Modifier.padding(horizontal = 2.dp).size(15.dp)
        )
        R.drawable.notification_dot -> Box(
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .size(8.dp)
                .background(Color(p.textColor), androidx.compose.foundation.shape.CircleShape)
        )
        else -> Image(
            painter = painterResource(res),
            contentDescription = null,
            modifier = Modifier.padding(horizontal = 2.dp),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(p.textColor)),
        )
    }
}
