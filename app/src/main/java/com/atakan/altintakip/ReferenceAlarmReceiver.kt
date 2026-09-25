package com.atakan.altintakip
import android.content.*
import androidx.work.*

class ReferenceAlarmReceiver:BroadcastReceiver(){
 override fun onReceive(context:Context,intent:Intent?){
  val req=OneTimeWorkRequestBuilder<GoldWorker>()
   .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
  WorkManager.getInstance(context).enqueueUniqueWork("daily_reference_fetch",ExistingWorkPolicy.REPLACE,req)
  ReferenceScheduler.scheduleNext(context)
 }
}