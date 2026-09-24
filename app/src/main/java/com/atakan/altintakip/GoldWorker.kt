package com.atakan.altintakip
import android.app.*
import android.content.*
import androidx.core.app.NotificationCompat
import androidx.work.*
import org.json.JSONObject
import java.net.URL
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.abs
class GoldWorker(ctx:Context,p:WorkerParameters):Worker(ctx,p){
 override fun doWork():Result=try{
  val xau=JSONObject(URL("https://api.gold-api.com/price/XAU").readText()).getDouble("price")
  val fx=JSONObject(URL("https://api.frankfurter.app/latest?from=USD&to=TRY").readText()).getJSONObject("rates").getDouble("TRY")
  val gram=xau*fx/31.1034768
  val sp=applicationContext.getSharedPreferences("p",Context.MODE_PRIVATE);val zone=ZoneId.of("Europe/Istanbul");val day=LocalDate.now(zone).toString()
  if(sp.getString("day","")!=day)sp.edit().putString("day",day).putLong("xau0",java.lang.Double.doubleToRawLongBits(xau)).putLong("gram0",java.lang.Double.doubleToRawLongBits(gram)).putBoolean("xAlert",false).putBoolean("gAlert",false).apply()
  val x0=java.lang.Double.longBitsToDouble(sp.getLong("xau0",java.lang.Double.doubleToRawLongBits(xau)));val g0=java.lang.Double.longBitsToDouble(sp.getLong("gram0",java.lang.Double.doubleToRawLongBits(gram)))
  val xp=(xau/x0-1)*100;val gp=(gram/g0-1)*100;val th=sp.getFloat("threshold",2f).toDouble()
  fun alarm(name:String,pct:Double,price:String,key:String){val outside=abs(pct)>=th;val alerted=sp.getBoolean(key,false)
   if(outside&&!alerted){val nm=applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;val ch="gold"
    if(android.os.Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(NotificationChannel(ch,"Altın Alarmları",NotificationManager.IMPORTANCE_HIGH))
    val dir=if(pct>0)"yükseldi" else "düştü";nm.notify(key.hashCode(),NotificationCompat.Builder(applicationContext,ch).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(name+" alarmı").setContentText(name+" bugün %"+"%.2f".format(abs(pct))+" "+dir+". Güncel: "+price).setPriority(NotificationCompat.PRIORITY_HIGH).build())}
   if(outside!=alerted)sp.edit().putBoolean(key,outside).apply()}
  alarm("Ons Altın",xp,"$"+"%.2f".format(xau),"xAlert");alarm("Gram Altın",gp,"%.2f TL".format(gram),"gAlert")
  sp.edit().putString("xau","$"+"%.2f".format(xau)).putString("gram","%.2f TL".format(gram)).putString("xauPct","%+.2f%%".format(xp)).putString("gramPct","%+.2f%%".format(gp)).putString("last",LocalDateTime.now(zone).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).apply()
  Result.success()
 }catch(e:Exception){Result.retry()}
}