# Barkod Okuyucu (Android)

Barkodları kamera ile okur, seçilen bölümle (CNC / FREZE / DİZAYN / KESME / SON KONTROL / ARA KONTROL / ELEKTRİK) birlikte CSV dosyasına kaydeder.

- **En yeni APK:** https://github.com/muh4mmedemin/Tek-Seferlik-Proje/releases/latest/download/BarkodOkuyucu.apk
- **Eski sürümler:** GitHub *Releases* sayfasında her sürüm (v1.5, v1.6, ...) ayrı durur.
- Yeni sürüm çıkarmak için `app/build.gradle` içindeki `versionCode` ve `versionName` artırılır.
- **Kayıt dosyası:** Telefonda `İndirilenler/barkod_kayitlari.csv`
  (yedek: `Android/data/com.muhammed.barkod/files/barkod_kayitlari.csv`)
- Format: `Tarih;Saat;Bolum;Barkod` (Excel'de doğrudan açılır)
- Bluetooth / el tipi okuyucu ile "Elle barkod gir" kutusuna okutup Enter ile de kaydedilebilir.
- Yeni bölüm eklemek için: `activity_main.xml` içindeki `bolumGrubu`na Button eklemek ve `Tasarim.bolumRengi()`ne renk vermek yeterli.
