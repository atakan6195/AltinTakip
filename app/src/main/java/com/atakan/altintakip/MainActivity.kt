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
 private lateinit var refXau:EditText
 private lateinit var refGram:EditText
 private val prefs by lazy { getSharedPreferences("p",MODE_PRIVATE) }

 override fun onCreate(b:Bundle?){super.onCreate(b)
  if(android.os.Build.VERSION.SDK_INT>=33) ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.POST_NOTIFICATIONS),1)
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(48,60,48,30)}
  info=TextView(this).apply{textSize=18f}
  val threshold=EditText(this).apply{hint="Alarm yüzdesi";setText(prefs.getFloat("threshold",2f).toString().replace(".",","));inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL}
  refXau=EditText(this).apply{hint="Ons referans ($)";inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL}
  refGram=EditText(this).apply{hint="Gram referans (TL)";inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL}
  val refOk=Button(this).apply{text="✓ Referansı Uygula"}
  val save=Button(this).apply{text="Alarmı Kaydet ve Takibi Başlat"}
  val refresh=Button(this).apply{text="Şimdi Kontrol Et"}
  root.addView(TextView(this).apply{text="ALTIN TAKİP";textSize=28f});root.addView(info)
  root.addView(TextView(this).apply{text="Referans değerleri (gerekirse elle düzeltilebilir):"})
  root.addView(refXau);root.addView(refGram);root.addView(refOk);root.addView(threshold);root.addView(save);root.addView(refresh);setContentView(root)
  show()
  refOk.setOnClickListener{
   val x=refXau.text.toString().replace(",",".").toDoubleOrNull();val gr=refGram.text.toString().replace(",",".").toDoubleOrNull()
   if(x==null||gr==null||x<=0||gr<=0){Toast.makeText(this,"Geçerli iki referans değeri girin",Toast.LENGTH_SHORT).show();return@setOnClickListener}
   val today=java.time.LocalDate.now(java.time.ZoneId.of("Europe/Istanbul")).toString()
   prefs.edit().putString("day",today).putLong("xau0",java.lang.Double.doubleToRawLongBits(x)).putLong("gram0",java.lang.Double.doubleToRawLongBits(gr))
    .putString("referenceTime","Elle düzeltildi").putString("referenceSource","Manuel").putBoolean("xAlert",false).putBoolean("gAlert",false).apply()
   Toast.makeText(this,"Referans değerleri uygulandı",Toast.LENGTH_SHORT).show();show()
  }
  save.setOnClickListener{
   val t=threshold.text.toString().trim().replace(",",".").toFloatOrNull()
   if(t==null||t<=0f){Toast.makeText(this,"Örn. 1,5 veya 1.5 girin",Toast.LENGTH_SHORT).show();return@setOnClickListener}
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
    if(wi!=null&&wi.state.isFinished){show();refresh.isEnabled=true;refresh.text="Şimdi Kontrol Et"
     Toast.makeText(this,if(wi.state==WorkInfo.State.SUCCEEDED)"Fiyatlar güncellendi" else "Fiyat alınamadı",Toast.LENGTH_SHORT).show()}
   }
  }
 }
 override fun onResume(){super.onResume();if(::info.isInitialized)show()}
 private fun show(){
  val xd=prefs.getLong("xau0",0L);val gd=prefs.getLong("gram0",0L)
  if(xd!=0L)refXau.setText("%.2f".format(java.lang.Double.longBitsToDouble(xd)).replace(".",","))
  if(gd!=0L)refGram.setText("%.2f".format(java.lang.Double.longBitsToDouble(gd)).replace(".",","))
  info.text="\nOns Altın: "+prefs.getString("xau","Henüz veri yok")+"\nGram Altın: "+prefs.getString("gram","Henüz veri yok")+
   "\n\nGünlük değişim (ons): "+prefs.getString("xauPct","-")+"\nGünlük değişim (gram): "+prefs.getString("gramPct","-")+
   "\nReferans zamanı: "+prefs.getString("referenceTime","Henüz alınmadı")+"\nReferans kaynağı: "+prefs.getString("referenceSource","-")+
   "\n\nPil dostu arka plan kontrolü: ~15 dk\nSon kontrol: "+prefs.getString("last","-")
 }
}