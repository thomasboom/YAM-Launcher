package eu.ottop.yamlauncher.tasks

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object NotificationEventBus {
    private val _notificationsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val notificationsChanged: SharedFlow<Unit> = _notificationsChanged

    fun emitNotificationsChanged() {
        _notificationsChanged.tryEmit(Unit)
    }
}
