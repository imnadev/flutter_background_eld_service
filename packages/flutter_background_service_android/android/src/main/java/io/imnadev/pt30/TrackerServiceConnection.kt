package io.imnadev.pt30

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder

class TrackerServiceConnection : ServiceConnection {

    var bound = false

    var service: TrackerService? = null

    override fun onServiceConnected(className: ComponentName, binder: IBinder) {
        service = (binder as TrackerService.TrackerBinder).getService()
        bound = true
    }

    override fun onServiceDisconnected(className: ComponentName) {
        bound = false
    }
}