package com.atakan.altintakip
import android.content.*
class BootReceiver:BroadcastReceiver(){
 override fun onReceive(context:Context,intent:Intent?){
  if(intent?.action==Intent.ACTION_BOOT_COMPLETED)ReferenceScheduler.scheduleNext(context)
 }
}