package com.atakan.altintakip
import android.Manifest
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

class MainActivity:AppCompatActivity(){
 private lateinit var info:TextView
 private val prefs by lazy { getSharedPreferences("p",MODE_PRIVATE) }

 override fun onCreate(b:Bundle?){super.onCreate(b)
  if(android.os.Build.VERSION.SDK_INT>=33) ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.POST_NOTIFICATIONS),1)
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(48,70,48,30)}
  info=TextView(this).apply{textSize=19f}
  val threshold=EditText(this).apply{
   hint="Alarm yüzdesi"
   setText(prefs.getFloat("threshold",2f).toString().replace(".",","))
   inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
  }
  val save=Button(this).apply{text="Alarmı Kaydet ve Takibi Başlat"}
  val refresh=Button(this).apply{text="Şimdi Kontrol Et"}
  root.addView(TextView(this).apply{text="ALTIN TAKİP";textSize=28f});root.addView(info);root.addView(threshold);root.addView(save);root.addView(refresh);setContentView(root)
  show()
  save.setOnClickListener{
   val t=threshold.text.toString().trim().replace(",",".").toFloatOrNull()
   if(t==null || t<=0f){Toast.makeText(this,"Örn. 1,5 veya 1.5 girin",Toast.LENGTH_SHORT).show();return@setOnClickListener}
   prefs.edit().putFloat("threshold",t).apply()
   val req=PeriodicWorkRequestBuilder<GoldWorker>(15,TimeUnit.MINUTES).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
   WorkManager.getInstance(this).enqueueUniquePeriodicWork("gold",ExistingPeriodicWorkPolicy.UPDATE,req)
   Toast.makeText(this,"Takip aktif: ±$t%",Toast.LENGTH_SHORT).show()
  }
  refresh.setOnClickListener{
   refresh.isEnabled=false;refresh.text="Kontrol ediliyor..."
   val request=OneTimeWorkRequestBuilder<GoldWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
   WorkManager.getInstance(this).enqueue(request)
   WorkManager.getInstance(this).getWorkInfoByIdLiveData(request.id).observe(this){wi->
    if(wi!=null && wi.state.isFinished){
     show();refresh.isEnabled=true;refresh.text="Şimdi Kontrol Et"
     if(wi.state==WorkInfo.State.SUCCEEDED) Toast.makeText(this,"Fiyatlar güncellendi",Toast.LENGTH_SHORT).show()
     else Toast.makeText(this,"Fiyat alınamadı. İnternet bağlantısını kontrol edin.",Toast.LENGTH_LONG).show()
    }
   }
  }
 }
 override fun onResume(){super.onResume();if(::info.isInitialized)show()}
 private fun show(){
  info.text="\nOns Altın: "+prefs.getString("xau","Henüz veri yok")+
   "\nGram Altın: "+prefs.getString("gram","Henüz veri yok")+
   "\n\nGünlük değişim (ons): "+prefs.getString("xauPct","-")+
   "\nGünlük değişim (gram): "+prefs.getString("gramPct","-")+
   "\nReferans zamanı: "+prefs.getString("referenceTime","Henüz alınmadı")+"\n\nPil dostu arka plan kontrolü: ~15 dk"+
   "\nSon kontrol: "+prefs.getString("last","-")
 }
}