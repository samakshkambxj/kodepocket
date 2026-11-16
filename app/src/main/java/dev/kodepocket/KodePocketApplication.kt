package dev.kodepocket

import android.app.Application
import dev.kodepocket.runtime.RuntimeSupervisor

class KodePocketApplication : Application() {

    /**
     * Process-scoped so the activity, the foreground service, and Compose all
     * observe the same runtime.
     */
    val supervisor: RuntimeSupervisor by lazy { RuntimeSupervisor(this) }
}
