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
  val root=JSONObject(text("https://xaus.com/api/v1/intraday?symbol=xau&hours=48"))
  val points=root.getJSONArray("points")
  var bestPrice:Double?=null;var bestTime:Instant?=null;var bestDiff=Long.MAX_VALUE
  for(i in 0 until points.length()){
   val p=points.getJSONObject(i)
   val raw=p.get("t")
   val instant=when(raw){
    is Number -> Instant.ofEpochSecond(raw.toLong())
    else -> Instant.parse(raw.toString())
   }
   val diff=kotlin.math.abs(instant.epochSecond-target.toEpochSecond())
   if(diff<bestDiff){bestDiff=diff;bestPrice=p.getDouble("p");bestTime=instant}
  }
  if(bestPrice==null||bestTime==null||bestDiff>20*60)return null
  return bestPrice!! to bestTime!!.atZone(zone).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
 }


 override fun doWork():Result=try{
  val live=JSONObject(text("https://xaus.com/api/v1/spot?currency=TRY&unit=gram&compact=1&fresh="+System.currentTimeMillis()))
  val state=live.optJSONObject("data_state")?.optString("status","fresh") ?: "fresh"
  if(state=="unavailable") return Result.retry()
  val xau=live.getDouble("spot_usd_oz")
  val gram=live.getJSONObject("xau").getDouble("price")
  val fx=live.optDouble("fx_rate",gram*31.1034768/xau)
  val sp=applicationContext.getSharedPreferences("p",Context.MODE_PRIVATE)
  val now=ZonedDateTime.now(zone)
  val today=now.toLocalDate().toString()

  // O günün referansı yoksa, Türkiye saati 00:05'e en yakın geçmiş 1 dakikalık altın verisini sonradan da çek.
  if(sp.getString("day","")!=today && !now.toLocalTime().isBefore(LocalTime.of(0,5))){
   val inWindow=now.toLocalTime().isBefore(LocalTime.of(0,25))
   val target=now.toLocalDate().atTime(0,5).atZone(zone)
   val hist=if(inWindow) Pair(xau,now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))) else historicalXau(target)
   if(hist!=null){
    val refXau=hist.first
    val refGram=refXau*fx/31.1034768
    sp.edit().putString("day",today)
     .putLong("xau0",java.lang.Double.doubleToRawLongBits(refXau))
     .putLong("gram0",java.lang.Double.doubleToRawLongBits(refGram))
     .putBoolean("xAlert",false).putBoolean("gAlert",false)
     .putString("referenceTime",hist.second)
     .putString("referenceSource",if(inWindow)"İnternet canlı veri" else "İnternet geçmiş veri").apply()
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