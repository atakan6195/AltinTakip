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
 private val zone=ZoneId.of("Europe/Istanbul")
 private fun text(url:String)=URL(url).openConnection().run{
  setRequestProperty("User-Agent","Mozilla/5.0 AltinTakip/1.7")
  connectTimeout=12000;readTimeout=12000;getInputStream().bufferedReader().use{it.readText()}
 }
 private fun historicalXau(target:ZonedDateTime):Pair<Double,String>?{
  val from=target.minusMinutes(8).toEpochSecond()
  val to=target.plusMinutes(12).toEpochSecond()
  val url="https://query1.finance.yahoo.com/v8/finance/chart/GC%3DF?period1=$from&period2=$to&interval=1m"
  val root=JSONObject(text(url)).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
  val ts=root.getJSONArray("timestamp")
  val closes=root.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).getJSONArray("close")
  var best=-1;var bestDiff=Long.MAX_VALUE
  for(i in 0 until ts.length()){
   if(closes.isNull(i))continue
   val diff=kotlin.math.abs(ts.getLong(i)-target.toEpochSecond())
   if(diff<bestDiff){best=i;bestDiff=diff}
  }
  if(best<0)return null
  val instant=Instant.ofEpochSecond(ts.getLong(best))
  return closes.getDouble(best) to instant.atZone(zone).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
 }
 private fun fxTry():Double=JSONObject(text("https://api.frankfurter.app/latest?from=USD&to=TRY")).getJSONObject("rates").getDouble("TRY")

 override fun doWork():Result=try{
  val xau=JSONObject(text("https://api.gold-api.com/price/XAU")).getDouble("price")
  val fx=fxTry()
  val gram=xau*fx/31.1034768
  val sp=applicationContext.getSharedPreferences("p",Context.MODE_PRIVATE)
  val now=ZonedDateTime.now(zone)
  val today=now.toLocalDate().toString()

  // O günün referansı yoksa, Türkiye saati 00:05'e en yakın geçmiş 1 dakikalık altın verisini sonradan da çek.
  if(sp.getString("day","")!=today && !now.toLocalTime().isBefore(LocalTime.of(0,5))){
   val target=now.toLocalDate().atTime(0,5).atZone(zone)
   val hist=historicalXau(target)
   if(hist!=null){
    // Gram referansı için mevcut FX yerine o günün günlük USD/TRY referansını kullanıyoruz.
    // FX kaynağı günlük olduğundan bu, 00:05'e en yakın mevcut TRY dönüşümüdür.
    val refXau=hist.first
    val refGram=refXau*fx/31.1034768
    sp.edit().putString("day",today)
     .putLong("xau0",java.lang.Double.doubleToRawLongBits(refXau))
     .putLong("gram0",java.lang.Double.doubleToRawLongBits(refGram))
     .putBoolean("xAlert",false).putBoolean("gAlert",false)
     .putString("referenceTime",hist.second).apply()
   }
  }

  val hasTodayReference=sp.getString("day","")==today
  if(hasTodayReference){
   val x0=java.lang.Double.longBitsToDouble(sp.getLong("xau0",0L))
   val g0=java.lang.Double.longBitsToDouble(sp.getLong("gram0",0L))
   val xp=(xau/x0-1)*100;val gp=(gram/g0-1)*100;val th=sp.getFloat("threshold",2f).toDouble()
   fun alarm(name:String,pct:Double,price:String,key:String){
    val outside=abs(pct)>=th;val alerted=sp.getBoolean(key,false)
    if(outside&&!alerted){
     val nm=applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;val ch="gold"
     if(android.os.Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(NotificationChannel(ch,"Altın Alarmları",NotificationManager.IMPORTANCE_HIGH))
     val dir=if(pct>0)"yükseldi" else "düştü"
     nm.notify(key.hashCode(),NotificationCompat.Builder(applicationContext,ch).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(name+" alarmı").setContentText(name+" bugün %"+"%.2f".format(abs(pct))+" "+dir+". Güncel: "+price).setPriority(NotificationCompat.PRIORITY_HIGH).build())
    }
    if(outside!=alerted)sp.edit().putBoolean(key,outside).apply()
   }
   alarm("Ons Altın",xp,"$"+"%.2f".format(xau),"xAlert");alarm("Gram Altın",gp,"%.2f TL".format(gram),"gAlert")
   sp.edit().putString("xauPct","%+.2f%%".format(xp)).putString("gramPct","%+.2f%%".format(gp)).apply()
  }else sp.edit().putString("xauPct","00:05 geçmiş verisi alınamadı").putString("gramPct","00:05 geçmiş verisi alınamadı").apply()

  sp.edit().putString("xau","$"+"%.2f".format(xau)).putString("gram","%.2f TL".format(gram))
   .putString("last",now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).apply()
  Result.success()
 }catch(e:Exception){Result.retry()}
}