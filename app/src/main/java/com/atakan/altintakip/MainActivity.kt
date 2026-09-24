package com.atakan.altintakip
import android.Manifest
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.work.*
import java.util.concurrent.TimeUnit
class MainActivity:AppCompatActivity(){
 override fun onCreate(b:Bundle?){super.onCreate(b)
  if(android.os.Build.VERSION.SDK_INT>=33) ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.POST_NOTIFICATIONS),1)
  val p=getSharedPreferences("p",MODE_PRIVATE)
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(48,70,48,30)}
  val info=TextView(this).apply{textSize=19f};val threshold=EditText(this).apply{hint="Alarm yüzdesi";setText(p.getFloat("threshold",2f).toString());inputType=2}
  val save=Button(this).apply{text="Alarmı Kaydet ve Takibi Başlat"};val refresh=Button(this).apply{text="Şimdi Kontrol Et"}
  root.addView(TextView(this).apply{text="ALTIN TAKİP";textSize=28f});root.addView(info);root.addView(threshold);root.addView(save);root.addView(refresh);setContentView(root)
  fun show(){info.text="\nOns Altın: "+p.getString("xau","Henüz veri yok")+"\nGram Altın: "+p.getString("gram","Henüz veri yok")+"\n\nGünlük değişim (ons): "+p.getString("xauPct","-")+"\nGünlük değişim (gram): "+p.getString("gramPct","-")+"\n\nPil dostu arka plan kontrolü: ~15 dk\nSon kontrol: "+p.getString("last","-")}
  show()
  save.setOnClickListener{val t=threshold.text.toString().replace(",",".").toFloatOrNull()?:2f;p.edit().putFloat("threshold",t).apply()
   val req=PeriodicWorkRequestBuilder<GoldWorker>(15,TimeUnit.MINUTES).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
   WorkManager.getInstance(this).enqueueUniquePeriodicWork("gold",ExistingPeriodicWorkPolicy.UPDATE,req);Toast.makeText(this,"Takip aktif: ±$t%",Toast.LENGTH_SHORT).show()}
  refresh.setOnClickListener{WorkManager.getInstance(this).enqueue(OneTimeWorkRequestBuilder<GoldWorker>().build());Toast.makeText(this,"Kontrol başlatıldı",Toast.LENGTH_SHORT).show()}
 }
}