package com.muhammed.barkod;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;

/** Renkler (res/values/colors.xml) uzerinden ortak buton / kutu gorunumleri. */
public final class Tasarim {

    private Tasarim() {
    }

    public static int renk(Context c, int id) {
        return c.getColor(id);
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static int bolumRengi(Context c, String bolum) {
        switch (bolum) {
            case "Freze":
                return renk(c, R.color.bolum_freze);
            case "Kesme":
                return renk(c, R.color.bolum_kesme);
            case "Ofis":
                return renk(c, R.color.bolum_ofis);
            case "CNC":
            default:
                return renk(c, R.color.bolum_cnc);
        }
    }

    public static GradientDrawable kutu(Context c, int dolgu, int kenar, float yaricapDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(dolgu);
        g.setCornerRadius(dp(c, yaricapDp));
        if (kenar != Color.TRANSPARENT) {
            g.setStroke(dp(c, 1.5f), kenar);
        }
        return g;
    }

    private static Drawable dalgali(int dalgaRengi, Drawable icerik) {
        return new RippleDrawable(ColorStateList.valueOf(dalgaRengi), icerik, null);
    }

    private static int saydam(int renk, int alfa) {
        return Color.argb(alfa, Color.red(renk), Color.green(renk), Color.blue(renk));
    }

    /** Dolu, renkli ana buton. */
    public static void birincil(Button b, int renk) {
        Context c = b.getContext();
        b.setBackground(dalgali(saydam(renk(c, R.color.bolum_uzeri), 60), kutu(c, renk, Color.TRANSPARENT, 16)));
        b.setTextColor(renk(c, R.color.bolum_uzeri));
    }

    /** Cerceveli, ikincil buton. */
    public static void ikincil(Button b) {
        Context c = b.getContext();
        int vurgu = renk(c, R.color.vurgu);
        b.setBackground(dalgali(saydam(vurgu, 40),
                kutu(c, renk(c, R.color.yuzey), renk(c, R.color.cizgi), 12)));
        b.setTextColor(renk(c, R.color.metin));
    }

    /** Bolum secim butonu: seciliyse bolum renginde dolu, degilse cerceveli. */
    public static void bolumSegmenti(RadioButton r, String bolum, boolean secili) {
        Context c = r.getContext();
        int renk = bolumRengi(c, bolum);
        r.setButtonDrawable(null);
        r.setGravity(Gravity.CENTER);
        r.setTextSize(16);
        r.setPadding(0, 0, 0, 0);
        r.setTypeface(null, secili ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        if (secili) {
            r.setBackground(kutu(c, renk, Color.TRANSPARENT, 12));
            r.setTextColor(renk(c, R.color.bolum_uzeri));
        } else {
            r.setBackground(dalgali(saydam(renk, 50),
                    kutu(c, renk(c, R.color.yuzey), renk(c, R.color.cizgi), 12)));
            r.setTextColor(renk);
        }
    }

    /** Beyaz/koyu yuzey kutusu (liste, giris alani). */
    public static void yuzey(View v) {
        Context c = v.getContext();
        v.setBackground(kutu(c, renk(c, R.color.yuzey), renk(c, R.color.cizgi), 12));
    }
}
