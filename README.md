# Barkod Okuyucu (Android)

Barkodları kamera ile okur, seçilen bölümle (Kesme / Freze) birlikte CSV dosyasına kaydeder.

- **APK indirme:** GitHub'da *Releases → Barkod Okuyucu APK → BarkodOkuyucu.apk*
- **Kayıt dosyası:** Telefonda `İndirilenler/barkod_kayitlari.csv`
  (yedek: `Android/data/com.muhammed.barkod/files/barkod_kayitlari.csv`)
- Format: `Tarih;Saat;Bolum;Barkod` (Excel'de doğrudan açılır)
- Bluetooth / el tipi okuyucu ile "Elle barkod gir" kutusuna okutup Enter ile de kaydedilebilir.
- Yeni bölüm eklemek için: `activity_main.xml` içine RadioButton ekleyip `seciliBolum()` güncellenir.
