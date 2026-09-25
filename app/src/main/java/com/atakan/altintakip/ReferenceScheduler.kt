package com.atakan.altintakip
import android.app.*
import android.content.*
import android.os.Build
import java.time.*

object ReferenceScheduler{
 fun scheduleNext(context:Context){
  val zone=ZoneId.of("Europe/Istanbul");val now=ZonedDateTime.now(zone)
  var next=now.toLocalDate().atTime(0,5).atZone(zone)
  if(!next.isAfter(now))next=next.plusDays(1)
  val am=context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
  val pi=PendingIntent.getBroadcast(context,1001,Intent(context,ReferenceAlarmReceiver::class.java),
   PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  if(Build.VERSION.SDK_INT>=31 && am.canScheduleExactAlarms())
   am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.toInstant().toEpochMilli(),pi)
  else
   am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.toInstant().toEpochMilli(),pi)
 }
}