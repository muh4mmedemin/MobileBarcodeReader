package com.muhammed.barkod;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.android.BeepManager;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;

import java.util.Arrays;
import java.util.Collection;

/** Kamera acik kalir; her okunan barkod aninda kaydedilir, "Bitir"e basilana kadar okumaya devam eder. */
public class SeriTaramaActivity extends Activity {

    public static final String EXTRA_BOLUM = "bolum";

    /**
     * Sadece bu turler okunur. ITF, Codabar, UPC/EAN, RSS gibi zayif kontrollu 1D turler
     * kapali: rastgele sayi okumalarinin (yanlis pozitif) asil kaynagi onlar.
     */
    private static final Collection<BarcodeFormat> TURLER = Arrays.asList(
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39,
            BarcodeFormat.CODE_93,
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX);

    /** Bundan kisa okumalar yok sayilir. */
    private static final int EN_AZ_UZUNLUK = 4;
    /** Kayit icin ayni deger art arda kac kez okunmali (tek seferlik yanlis okumayi eler). */
    private static final int DOGRULAMA_SAYISI = 2;
    /** Dogrulama okumalari arasindaki en uzun sure. */
    private static final long DOGRULAMA_SURESI_MS = 1500;
    /** Ayni barkod bu sure icinde tekrar okunursa yeniden kaydedilmez (kamera ayni kodu art arda gorur). */
    private static final long TEKRAR_SURESI_MS = 3000;
    private static final int KAMERA_IZNI = 10;

    private DecoratedBarcodeView barkodView;
    private BeepManager beepManager;
    private TextView bolumView;
    private TextView sayacView;
    private TextView durumView;
    private Button btnFener;
    private boolean fenerAcik;
    private String bolum;

    private String aday;
    private int adaySayisi;
    private long adayZaman;

    private String sonBarkod;
    private long sonZaman;
    private int buOturumdaSayi;

    private final BarcodeCallback callback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            try {
                okumaGeldi(result.getText());
            } catch (Throwable t) {
                HataKaydi.yaz(SeriTaramaActivity.this, t);
                durumGoster("HATA: " + t, R.color.hata);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_seri_tarama);

        bolum = getIntent().getStringExtra(EXTRA_BOLUM);
        if (bolum == null) {
            bolum = "CNC";
        }
        int bolumRengi = Tasarim.bolumRengi(this, bolum);

        barkodView = findViewById(R.id.barkodView);
        bolumView = findViewById(R.id.bolum);
        sayacView = findViewById(R.id.sayac);
        durumView = findViewById(R.id.durum);
        btnFener = findViewById(R.id.btnFener);
        Button btnBitir = findViewById(R.id.btnBitir);

        findViewById(R.id.ustBant).setBackgroundColor(bolumRengi);
        bolumView.setText(bolum);
        sayacView.setText("0");
        durumGoster("Barkodu çerçevenin ortasına getirin", 0);

        Tasarim.birincil(btnBitir, bolumRengi);
        btnBitir.setOnClickListener(v -> finish());
        Tasarim.ikincil(btnFener);
        btnFener.setOnClickListener(v -> fenerDegistir());
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            btnFener.setEnabled(false);
            btnFener.setAlpha(0.4f);
        }

        barkodView.getBarcodeView().setDecoderFactory(new DefaultDecoderFactory(TURLER));
        barkodView.setStatusText("");
        beepManager = new BeepManager(this);
        beepManager.setVibrateEnabled(true);

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, KAMERA_IZNI);
        }
        barkodView.decodeContinuous(callback);
    }

    private void okumaGeldi(String barkod) {
        if (barkod == null) {
            return;
        }
        barkod = barkod.trim();
        if (barkod.length() < EN_AZ_UZUNLUK) {
            return;
        }
        long simdi = System.currentTimeMillis();

        // 1) Dogrulama: ayni deger kisa sure icinde art arda okunmali
        if (barkod.equals(aday) && simdi - adayZaman < DOGRULAMA_SURESI_MS) {
            adaySayisi++;
        } else {
            aday = barkod;
            adaySayisi = 1;
        }
        adayZaman = simdi;
        if (adaySayisi < DOGRULAMA_SAYISI) {
            return;
        }

        // 2) Ayni barkod kamerada durdukca tekrar kaydetme
        if (barkod.equals(sonBarkod) && simdi - sonZaman < TEKRAR_SURESI_MS) {
            sonZaman = simdi;
            return;
        }
        sonBarkod = barkod;
        sonZaman = simdi;
        barkodKaydet(barkod);
    }

    private void barkodKaydet(String barkod) {
        try {
            String hata = KayitDeposu.kaydet(this, bolum, barkod);
            buOturumdaSayi++;
            sayacView.setText(String.valueOf(buOturumdaSayi));
            try {
                beepManager.playBeepSoundAndVibrate();
            } catch (Throwable t) {
                HataKaydi.yaz(this, t);
            }
            String metin = "✔  " + barkod;
            if (hata != null) {
                metin += "\nİndirilenler kopyası güncellenemedi: " + hata;
            }
            durumGoster(metin, R.color.basari);
        } catch (Exception e) {
            durumGoster("Kaydedilemedi: " + e.getMessage(), R.color.hata);
            Toast.makeText(this, "KAYIT HATASI: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** renkId 0 ise notr (yari saydam siyah) zemin. */
    private void durumGoster(String metin, int renkId) {
        int zemin = renkId == 0 ? Color.argb(170, 0, 0, 0) : getColor(renkId);
        durumView.setBackground(Tasarim.kutu(this, zemin, Color.TRANSPARENT, 12));
        durumView.setTextColor(renkId == 0 ? Color.WHITE : getColor(R.color.bolum_uzeri));
        durumView.setText(metin);
    }

    private void fenerDegistir() {
        fenerAcik = !fenerAcik;
        if (fenerAcik) {
            barkodView.setTorchOn();
            btnFener.setText("Feneri kapat");
        } else {
            barkodView.setTorchOff();
            btnFener.setText("Fener");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == KAMERA_IZNI) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                barkodView.resume();
            } else {
                Toast.makeText(this, "Barkod okumak için kamera izni gerekli", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            barkodView.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        barkodView.pause();
        if (fenerAcik) {
            fenerAcik = false;
            btnFener.setText("Fener");
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return barkodView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }
}
