package dev.kodepocket

import android.app.Application
import dev.kodepocket.chat.ChatStore
import dev.kodepocket.runtime.RuntimeSupervisor

class KodePocketApplication : Application() {

    /**
     * Process-scoped so the activity, the foreground service, and Compose all
     * observe the same runtime.
     */
    val supervisor: RuntimeSupervisor by lazy { RuntimeSupervisor(this) }

    /**
     * Also process-scoped: the event stream and the active chat survive
     * activity recreation, so a rotating screen never drops a streaming turn.
     */
    val chat: ChatStore by lazy { ChatStore(this, supervisor) }
}
