package com.muhammed.barkod;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;

public class MainActivity extends Activity {

    private SharedPreferences prefs;
    /** Bolum butonlari (yazilari bolum adidir). */
    private final java.util.List<Button> bolumButonlari = new java.util.ArrayList<>();
    private String seciliBolumAdi;
    private EditText elleGiris;
    private TextView kayitlarView;
    private TextView dosyaYoluView;
    private static final int DOSYA_SEC = 20;
    private static final int DOSYA_OLUSTUR = 21;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HataKaydi.kur(this);
        setContentView(R.layout.activity_main);
        try {
            String surum = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            setTitle("Barkod Okuyucu  v" + surum);
        } catch (Exception ignored) {
        }
        HataKaydi.varsaGoster(this);

        prefs = getSharedPreferences("ayarlar", MODE_PRIVATE);
        butonlariTopla(findViewById(R.id.bolumGrubu));
        elleGiris = findViewById(R.id.elleGiris);
        kayitlarView = findViewById(R.id.kayitlar);
        dosyaYoluView = findViewById(R.id.dosyaYolu);

        String kayitliBolum = prefs.getString("bolum", "CNC");
        seciliBolumAdi = bolumButonlari.get(0).getText().toString();
        for (Button b : bolumButonlari) {
            String ad = b.getText().toString();
            // eski surumlerde kaydedilen "Freze" gibi adlar da eslessin
            if (ad.equalsIgnoreCase(kayitliBolum)
                    || ad.equals(kayitliBolum.toUpperCase(new java.util.Locale("tr", "TR")))) {
                seciliBolumAdi = ad;
            }
            b.setOnClickListener(v -> {
                seciliBolumAdi = ad;
                prefs.edit().putString("bolum", ad).apply();
                gorunumuGuncelle();
            });
        }

        Tasarim.yuzey(findViewById(R.id.elleKutu));
        Tasarim.yuzey(findViewById(R.id.kayitKutu));
        Tasarim.ikincil(findViewById(R.id.btnPaylas));
        Tasarim.ikincil(findViewById(R.id.btnSonuSil));
        gorunumuGuncelle();

        findViewById(R.id.btnTara).setOnClickListener(v -> taramaBaslat());
        findViewById(R.id.btnKaydet).setOnClickListener(v -> elleKaydet());

        // El tipi / bluetooth barkod okuyucular klavye gibi yazip Enter gonderir
        elleGiris.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN);
            if (enter) {
                elleKaydet();
                return true;
            }
            return false;
        });

        findViewById(R.id.btnPaylas).setOnClickListener(v -> dosyayiPaylas());
        findViewById(R.id.btnSonuSil).setOnClickListener(v -> sonKaydiSilSor());

        Tasarim.ikincil(findViewById(R.id.btnDosyaSec));
        Tasarim.ikincil(findViewById(R.id.btnYeniDosya));
        Tasarim.ikincil(findViewById(R.id.btnOtomatik));
        findViewById(R.id.btnDosyaSec).setOnClickListener(v -> dosyaSec());
        findViewById(R.id.btnYeniDosya).setOnClickListener(v -> yeniDosya());
        findViewById(R.id.btnOtomatik).setOnClickListener(v -> {
            KayitDeposu.seciliDosyaKaldir(this);
            Toast.makeText(this, "Otomatik dosyaya dönüldü", Toast.LENGTH_SHORT).show();
            dosyaBilgisiniGuncelle();
            listeyiGuncelle();
        });

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }

    }

    private void dosyaBilgisiniGuncelle() {
        dosyaYoluView.setText(KayitDeposu.dosyaAdi(this));
        View otomatik = findViewById(R.id.btnOtomatik);
        boolean secili = KayitDeposu.seciliUri(this) != null;
        otomatik.setEnabled(secili);
        otomatik.setAlpha(secili ? 1f : 0.4f);
    }

    /** Var olan bir dosyayi sec: kayitlar o dosyanin sonuna eklenir. */
    private void dosyaSec() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, DOSYA_SEC);
    }

    /** Istenen yerde yeni bir CSV dosyasi olustur ve onu kullan. */
    private void yeniDosya() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/csv");
        String gun = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(new java.util.Date());
        i.putExtra(Intent.EXTRA_TITLE, "barkod_" + gun + ".csv");
        startActivityForResult(i, DOSYA_OLUSTUR);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode != DOSYA_SEC && requestCode != DOSYA_OLUSTUR)
                || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception e) {
            Toast.makeText(this, "Bu dosyaya kalıcı yazma izni alınamadı: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            return;
        }
        KayitDeposu.seciliDosyaAta(this, uri);
        Toast.makeText(this, "Kayıtlar bu dosyaya devam edecek", Toast.LENGTH_SHORT).show();
        dosyaBilgisiniGuncelle();
        listeyiGuncelle();
    }

    @Override
    protected void onResume() {
        super.onResume();
        dosyaBilgisiniGuncelle();
        listeyiGuncelle(); // seri taramadan donunce listeyi yenile
    }

    /** Bolum butonlarini, tarama butonunu ve Kaydet butonunu secili bolumun rengine boyar. */
    private void gorunumuGuncelle() {
        for (Button r : bolumButonlari) {
            String ad = r.getText().toString();
            Tasarim.bolumSegmenti(r, ad, ad.equals(seciliBolumAdi));
        }
        String bolum = seciliBolum();
        int renk = Tasarim.bolumRengi(this, bolum);
        Button tara = findViewById(R.id.btnTara);
        Tasarim.birincil(tara, renk);
        SpannableString metin = new SpannableString("Seri taramayı başlat\n" + bolum);
        int ayrim = metin.toString().indexOf('\n');
        metin.setSpan(new RelativeSizeSpan(0.7f), 0, ayrim, 0);
        metin.setSpan(new RelativeSizeSpan(1.3f), ayrim + 1, metin.length(), 0);
        tara.setText(metin);
        Tasarim.birincil(findViewById(R.id.btnKaydet), renk);
    }

    private String seciliBolum() {
        return seciliBolumAdi;
    }

    private void butonlariTopla(ViewGroup grup) {
        for (int i = 0; i < grup.getChildCount(); i++) {
            View v = grup.getChildAt(i);
            if (v instanceof Button) {
                bolumButonlari.add((Button) v);
            } else if (v instanceof ViewGroup) {
                butonlariTopla((ViewGroup) v);
            }
        }
    }

    private void taramaBaslat() {
        Intent i = new Intent(this, SeriTaramaActivity.class);
        i.putExtra(SeriTaramaActivity.EXTRA_BOLUM, seciliBolum());
        startActivity(i);
    }

    private void elleKaydet() {
        String barkod = elleGiris.getText().toString().trim();
        if (barkod.isEmpty()) {
            return;
        }
        String bolum = seciliBolum();
        try {
            String hata = KayitDeposu.kaydet(this, bolum, barkod);
            if (hata == null) {
                Toast.makeText(this, "Kaydedildi: " + bolum + " - " + barkod, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Kaydedildi. Uyarı: " + hata,
                        Toast.LENGTH_LONG).show();
            }
            elleGiris.setText("");
        } catch (IOException e) {
            Toast.makeText(this, "KAYIT HATASI: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        listeyiGuncelle();
    }

    private void listeyiGuncelle() {
        List<String> satirlar = KayitDeposu.satirlariOku(this);
        int kayitSayisi = Math.max(0, satirlar.size() - 1); // baslik haric
        ((TextView) findViewById(R.id.kayitSayisi)).setText("TOPLAM " + kayitSayisi);
        if (kayitSayisi == 0) {
            kayitlarView.setTextColor(getColor(R.color.soluk));
            kayitlarView.setText("Henüz kayıt yok. Bölümü seçip seri taramayı başlatın.");
            return;
        }
        kayitlarView.setTextColor(getColor(R.color.metin));
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int sinir = 0;
        for (int i = satirlar.size() - 1; i >= 1 && sinir < 100; i--, sinir++) {
            String[] p = satirlar.get(i).split(";", 4);
            if (p.length < 4) {
                sb.append(satirlar.get(i)).append("\n");
                continue;
            }
            String tarih = p[0].length() >= 5 ? p[0].substring(0, 5) : p[0]; // gg.aa
            int bas = sb.length();
            sb.append(tarih).append(' ').append(p[1]).append("  ");
            sb.setSpan(new ForegroundColorSpan(getColor(R.color.soluk)), bas, sb.length(), 0);
            bas = sb.length();
            sb.append(String.format(java.util.Locale.ROOT, "%-11s", p[2]));
            sb.setSpan(new ForegroundColorSpan(Tasarim.bolumRengi(this, p[2])), bas, sb.length(), 0);
            sb.setSpan(new StyleSpan(Typeface.BOLD), bas, sb.length(), 0);
            sb.append(' ').append(p[3]).append("\n");
        }
        kayitlarView.setText(sb);
    }

    private void sonKaydiSilSor() {
        List<String> satirlar = KayitDeposu.satirlariOku(this);
        if (satirlar.size() <= 1) {
            Toast.makeText(this, "Silinecek kayıt yok", Toast.LENGTH_SHORT).show();
            return;
        }
        String son = satirlar.get(satirlar.size() - 1);
        new AlertDialog.Builder(this)
                .setTitle("Son kayıt silinsin mi?")
                .setMessage(son.replace(";", "  "))
                .setPositiveButton("Sil", (d, w) -> {
                    satirlar.remove(satirlar.size() - 1);
                    try {
                        KayitDeposu.satirlariYaz(this, satirlar);
                        Toast.makeText(this, "Son kayıt silindi", Toast.LENGTH_SHORT).show();
                    } catch (IOException e) {
                        Toast.makeText(this, "Silme hatası: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                    listeyiGuncelle();
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private void dosyayiPaylas() {
        Uri secili = KayitDeposu.seciliUri(this);
        if (secili != null) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/csv");
            i.putExtra(Intent.EXTRA_STREAM, secili);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Barkod kayıtlarını paylaş"));
            return;
        }
        if (!KayitDeposu.anaDosya(this).exists()) {
            Toast.makeText(this, "Henüz kayıt yok", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            KayitDeposu.indirilenlereKopyala(this);
            uri = KayitDeposu.indirilenlerUri(this, false);
            if (uri == null) {
                Toast.makeText(this, "Dosya bulunamadı", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            // Kisisel kullanim: FileProvider yerine file:// paylasimina izin ver
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
            uri = Uri.fromFile(KayitDeposu.anaDosya(this));
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/csv");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Barkod kayıtlarını paylaş"));
    }
}
