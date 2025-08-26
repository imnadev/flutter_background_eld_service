/*
 * Copyright (c) 2015, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package io.imnadev.pt30

import android.app.NotificationManager
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.preference.PreferenceManager
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.pt.sdk.BaseResponse
import com.pt.sdk.BleuManager
import com.pt.sdk.SystemVar
import com.pt.sdk.TSError
import com.pt.sdk.TelemetryEvent
import com.pt.sdk.TrackerManager
import com.pt.sdk.TrackerManagerCallbacks
import com.pt.sdk.request.GetStoredEventsCount
import com.pt.sdk.request.GetSystemVar
import com.pt.sdk.request.GetTrackerInfo
import com.pt.sdk.request.RetrieveStoredEvents
import com.pt.sdk.request.inbound.SPNEventRequest
import com.pt.sdk.request.inbound.StoredTelemetryEventRequest
import com.pt.sdk.request.inbound.TelemetryEventRequest
import com.pt.sdk.response.ClearDiagTroubleCodesResponse
import com.pt.sdk.response.ClearStoredEventsResponse
import com.pt.sdk.response.ConfigureSPNEventResponse
import com.pt.sdk.response.GetDiagTroubleCodesResponse
import com.pt.sdk.response.GetStoredEventsCountResponse
import com.pt.sdk.response.GetSystemVarResponse
import com.pt.sdk.response.GetTrackerInfoResponse
import com.pt.sdk.response.GetVehicleInfoResponse
import com.pt.sdk.response.RetrieveStoredEventsResponse
import com.pt.sdk.response.SetSystemVarResponse
import com.pt.sdk.response.outbound.AckEvent
import com.pt.sdk.response.outbound.AckSPNEvent
import com.pt.ws.TrackerInfo
import no.nordicsemi.android.log.Logger
import timber.log.Timber

abstract class TrackerService : BleProfileService(), TrackerManagerCallbacks {

    private val log = Timber.tag("TrackerService")

    lateinit var mTracker: TrackerManager

    private val binder = TrackerBinder()

    override fun getBinder(): LocalBinder {
        return binder
    }

    inner class TrackerBinder : LocalBinder() {
        fun getService() = this@TrackerService
    }

    override fun initializeManager(): BleuManager {
        log.i("initializeManager: ")
        mTracker = TrackerManager(this)
        mTracker.setTrackerManagerCallbacks(this)
        AppModel.getInstance().vdbParams.clear()
        return mTracker
    }

    override fun shouldAutoConnect() = true

    override fun onCreate() {
        super.onCreate()
        registerReceiver(mDisconnectActionBroadcastReceiver, IntentFilter(ACTION_DISCONNECT))
    }

    override fun onBind(intent: Intent) = binder

    override fun onDestroy() {
        cancelNotifications()
        unregisterReceiver(mDisconnectActionBroadcastReceiver)
        super.onDestroy()
    }

    private fun syncTracker() {
        log.i("Get Tracker info ...")
        val getTrackerInfoRequest = GetTrackerInfo()
        mTracker.sendRequest(getTrackerInfoRequest, null, null)

        log.i("Get Stored Events count ...")
        val getStoredEventsCountRequest = GetStoredEventsCount()
        mTracker.sendRequest(getStoredEventsCountRequest, null, null)

        log.i("Retrieve Stored Events ...")
        mTracker.sendRequest(RetrieveStoredEvents(), null, null)

        mTracker.setVirtualDashboard(true)
    }

    override fun onDeviceReady(device: BluetoothDevice) {
        super.onDeviceReady(device)
        syncTracker()
    }

    override fun onSerialConnected(device: UsbDevice) {
        super.onSerialConnected(device)
        syncTracker()
    }

    override fun onDeviceDisconnected(device: BluetoothDevice, code: Int) {
        super.onDeviceDisconnected(device, code)
        cancelNotifications()
    }

    abstract fun onTelemetryEvent(event: TelemetryEvent)

    override fun onRequest(address: String, tmr: TelemetryEventRequest) {
        AppModel.getInstance().mLastEvent = tmr.mTm
        onTelemetryEvent(tmr.mTm)
        val broadcast = Intent(TrackerAction.REFRESH.action)
        broadcast.putExtra(EXTRA_DEVICE, bluetoothDevice)
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast)

        val dt = tmr.mTm.mDateTime

        log.i("EVENT:" + tmr.mTm.mEvent.toString() + ":" + tmr.mTm.mSeq)

        val ack = AckEvent(0, tmr.mTm.mSeq.toString(), dt.toDateString())
        mTracker.sendResponse(ack, null, null)
    }

    override fun onRequest(address: String, stmr: StoredTelemetryEventRequest) {
        val broadcast = Intent(TrackerAction.SE.action)
        AppModel.getInstance().mLastSEvent = stmr.mTm
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast)
    }

    override fun onRequest(address: String, spner: SPNEventRequest) {
        val broadcast = Intent(TrackerAction.SPN.action)
        AppModel.getInstance().mLastSPNEv = spner.mSPNEv
        AppModel.getInstance().mLastSen = spner.mSen
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast)

        val ack = AckSPNEvent(0, spner.mSen)
        mTracker.sendResponse(ack, null, null)
    }

    abstract fun onTrackerInfo(trackerInfo: TrackerInfo)

    override fun onResponse(address: String, tir: GetTrackerInfoResponse) {
        val ver = "F/W:" + tir.mTi.mvi.toString() + "  BLE:" + tir.mTi.bvi.toString()
        if (tir.status != 0) {
            log.w("GetTrackerInfoResponse: S=" + tir.status)
            return
        }
        val broadcast = Intent(TrackerAction.TRACKER.action)
        AppModel.getInstance().mTrackerInfo = tir.mTi
        onTrackerInfo(tir.mTi);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast)
        // PT30 Compatibility - Extract VIN, for PT30
        if (tir.mTi.product.contains("30") && tir.containsKey(BaseResponse.Key.VIN)) {
            // PT30 workaround - sends a null tag, if VIN is not present
            val vin = tir.getValue(BaseResponse.Key.VIN)
            if (!TextUtils.isEmpty(vin)) {
                AppModel.getInstance().mPT30Vin = vin
            } else {
                AppModel.getInstance().mPT30Vin = ""
            }
        }
        if (tir.mTi.product.contains("30")) {
            // Get Tracker system vars
            log.i("Get Tracker SV:PE ...")
            val gsv = GetSystemVar(SystemVar.PERIODIC_EVENT_GAP)
            mTracker.sendRequest(gsv, null, null)
        } else {
            // Get Tracker system vars
            log.i("Get Tracker SV:HUC ...")
            val gsv = GetSystemVar("HUC")
            mTracker.sendRequest(gsv, null, null)
        }
    }

    override fun onResponse(address: String, vir: GetVehicleInfoResponse) {
        if (vir.status != 0) {
            log.w("GetVehicleInfoResponse: S=" + vir.status)
            return
        }
        val broadcast = Intent(TrackerAction.VIN.action)
        AppModel.getInstance().mVehicleInfo = vir.mVi
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast)
    }

    override fun onResponse(address: String, rser: RetrieveStoredEventsResponse) {
        if (rser.status != 0) {
            log.w("RetrieveStoredEventsResponse: S=" + rser.status)
            return
        }
        // NOP - The events shall be updated in the Stored events tile
    }

    override fun onResponse(address: String, dtcr: GetDiagTroubleCodesResponse) {
        if (dtcr.status != 0) {
            log.w("GetDiagTroubleCodesResponse: S=" + dtcr.status)
            return
        }
        val broadcast = Intent(TrackerAction.DTC.action)
        broadcast.putExtra(EXTRA_RESP_ACTION_KEY, "GET")
        AppModel.getInstance().mLastDTC = dtcr.mDTC
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast)
    }

    override fun onResponse(address: String, gsecr: GetStoredEventsCountResponse) {
        if (gsecr.status != 0) {
            log.w("GetStoredEventsCountResponse: S=" + gsecr.status)
            return
        }
        val broadcast = Intent(TrackerAction.SE.action)
        AppModel.getInstance().mLastSECount = gsecr.mCount
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast)
    }

    override fun onResponse(address: String, cdtcr: ClearDiagTroubleCodesResponse) {
        if (cdtcr.status != 0) {
            log.w("GetDiagTroubleCodesResponse: S=" + cdtcr.status)
            return
        }
        val broadcast = Intent(TrackerAction.DTC.action)
        broadcast.putExtra(EXTRA_RESP_ACTION_KEY, "CLEAR")
        broadcast.putExtra(EXTRA_RESP_STATUS_KEY, cdtcr.status)
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast)
    }

    override fun onResponse(address: String, cser: ClearStoredEventsResponse) {
        if (cser.status != 0) {
            log.w("ClearStoredEventsResponse: S=" + cser.status)
            return
        }
        val broadcast = Intent(TrackerAction.SE.action)
        AppModel.getInstance().mLastSECount = 0
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast)
    }

    override fun onResponse(address: String, gsvr: GetSystemVarResponse) {
        if (gsvr.status != 0) {
            log.w("GetSystemVarResponse: S=" + gsvr.status)
            return
        }
        if (!TextUtils.isEmpty(gsvr.mTag) && gsvr.mTag == SystemVar.PERIODIC_EVENT_GAP.mVal) {
            // App model and shared pref
            AppModel.getInstance().mPE = gsvr.mVal
            log.d("SV: PE = " + gsvr.mVal)
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
            val editor = sharedPref.edit()
            editor.putString("sv_pe", gsvr.mVal)
            editor.commit()
        } else if (!TextUtils.isEmpty(gsvr.mTag) && gsvr.mTag == "HUC") { //PT-40
            // App model and shared pref
            AppModel.getInstance().mPE = gsvr.mVal
            log.d("SV: HUC = " + gsvr.mVal)
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
            val editor = sharedPref.edit()
            editor.putString("sv_pe", gsvr.mVal)
            editor.commit()
        }
    }

    override fun onResponse(address: String, ssvr: SetSystemVarResponse) {
        // NOP
    }

    override fun onResponse(s: String, configureSPNEventResponse: ConfigureSPNEventResponse) {
        // NOP
    }

    override fun onVirtualDashboardUpdated(address: String, updatedParams: List<Int>) {
        AppModel.getInstance().dashboard = mTracker.virtualDashboard.get()
        AppModel.getInstance().vdbParams.addAll(updatedParams)
        val broadcast = Intent(TrackerAction.DASHBOARD.action)
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast)
    }

    override fun onFwUptodate(address: String) {
        val intent = Intent(TrackerAction.TRACKER.action)
        intent.putExtra("action", EXTRA_TRACKER_UPDATE_ACTION_UPTODATE) // UPTODATE
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onFileUpdateStarted(address: String, fn: String) {
        createUpdateNotification("Updating $fn ...")
        val intent = Intent(TrackerAction.UPDATE.action)
        intent.putExtra("action", EXTRA_TRACKER_UPDATE_ACTION_STARTED) // STARTED
        intent.putExtra("arg", fn)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onFileUpdateProgress(address: String, percentage: Int) {
        val intent = Intent(TrackerAction.UPDATE.action)
        intent.putExtra("action", EXTRA_TRACKER_UPDATE_ACTION_PROG) // PROGRESS
        intent.putExtra("arg", percentage)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onFileUpdateCompleted(address: String) {
        cancelUpdateNotifications()
        val intent = Intent(TrackerAction.UPDATE.action)
        intent.putExtra("action", EXTRA_TRACKER_UPDATE_ACTION_COMPLETED) // COMPLETED
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onFileUpdateFailed(address: String, tsError: TSError) {
        cancelUpdateNotifications()
        val intent = Intent(TrackerAction.UPDATE.action)
        intent.putExtra("action", EXTRA_TRACKER_UPDATE_ACTION_FAILED) // FAILED
        intent.putExtra(TSError.KEY_CODE, tsError.mCode)
        intent.putExtra(TSError.KEY_CAUSE, tsError.mCause)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onFwUpdated(address: String, ti: TrackerInfo) {
        val intent = Intent(TrackerAction.UPDATE.action)
        intent.putExtra("action", EXTRA_TRACKER_UPDATE_ACTION_UPDATED) // UPDATED
        AppModel.getInstance().mTrackerInfo = ti
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun cancelNotifications() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(CONNECTION_NOTI_ID)
        nm.cancel(UPDATE_NOTI_ID)
    }

    private fun createUpdateNotification(msg: String) {
        val builder = NotificationCompat.Builder(this, "Notifications.UPDATE_CHANNEL")
        builder.setContentTitle("App name").setContentText(msg)
        val notification = builder.build()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(UPDATE_NOTI_ID, notification)
    }

    private fun cancelUpdateNotifications() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(UPDATE_NOTI_ID)
    }

    /**
     * This broadcast receiver listens for [.ACTION_DISCONNECT] that may be fired by pressing Disconnect action button on the notification.
     */
    private val mDisconnectActionBroadcastReceiver: BroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val source = intent.getIntExtra(EXTRA_SOURCE, SOURCE_NOTIFICATION)
                when (source) {
                    SOURCE_NOTIFICATION -> Logger.i(
                        logSession, "[Notification] Disconnect action pressed"
                    )

                    SOURCE_WEARABLE -> Logger.i(
                        logSession,
                        "[WEAR] '" + "Constants.ACTION_DISCONNECT" + "' message received"
                    )
                }
                if (isConnected) binder.disconnect(context) else stopSelf()
            }
        }

    companion object {

        /** Action send when user press the DISCONNECT button on the notification.  */
        const val ACTION_DISCONNECT = "no.nordicsemi.android.nrftoolbox.uart.ACTION_DISCONNECT"

        /** A source of an action.  */
        const val EXTRA_SOURCE = "no.nordicsemi.android.nrftoolbox.uart.EXTRA_SOURCE"
        const val SOURCE_NOTIFICATION = 0
        const val SOURCE_WEARABLE = 1
        const val SOURCE_3RD_PARTY = 2
        private const val CONNECTION_NOTI_ID = 151 // random
        private const val UPDATE_NOTI_ID = 171 // random
        private const val OPEN_ACTIVITY_REQ = 67 // random
        private const val DISCONNECT_REQ = 97 // random
        const val EXTRA_RESP_STATUS_KEY = "status"
        const val EXTRA_RESP_ACTION_KEY = "action"
        const val EXTRA_TRACKER_UPDATE_ACTION_KEY = "action"
        const val EXTRA_TRACKER_UPDATE_ARG_KEY = "arg"
        const val EXTRA_TRACKER_UPDATE_ACTION_UPTODATE = 0
        const val EXTRA_TRACKER_UPDATE_ACTION_STARTED = 1
        const val EXTRA_TRACKER_UPDATE_ACTION_PROG = 2
        const val EXTRA_TRACKER_UPDATE_ACTION_COMPLETED = 3
        const val EXTRA_TRACKER_UPDATE_ACTION_UPDATED = 4
        const val EXTRA_TRACKER_UPDATE_ACTION_FAILED = -1
    }
}