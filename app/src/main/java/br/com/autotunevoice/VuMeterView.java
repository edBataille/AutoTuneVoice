package br.com.autotunevoice;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Medidor de nível (VU) simples, sem dependências externas.
 * Mostra ao usuário que o áudio está entrando, com ataque rápido
 * (a barra sobe na hora) e queda suave (desce aos poucos), como um VU real.
 */
public class VuMeterView extends View {
    private float level = 0f; // nível já suavizado, de 0 a 1

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public VuMeterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        trackPaint.setColor(Color.parseColor("#1F000000"));
        tickPaint.setColor(Color.parseColor("#33000000"));
        tickPaint.setStrokeWidth(3f);
        setWillNotDraw(false);
    }

    /** Chamar a partir da thread de UI com o nível bruto (0..1) medido no buffer de áudio atual. */
    public void setLevel(float rawLevel) {
        float clamped = Math.max(0f, Math.min(1f, rawLevel));
        if (clamped > level) {
            level = clamped; // ataque: sobe imediatamente
        } else {
            level = level * 0.80f + clamped * 0.20f; // queda: decai suavemente
        }
        if (level < 0.005f) level = 0f;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        float radius = h / 2f;

        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, radius, radius, trackPaint);

        float barWidth = w * level;
        if (barWidth > 1f) {
            rect.set(0, 0, barWidth, h);
            barPaint.setColor(colorForLevel(level));
            canvas.drawRoundRect(rect, radius, radius, barPaint);
        }

        for (int i = 1; i < 10; i++) {
            float x = w * i / 10f;
            canvas.drawLine(x, 0, x, h, tickPaint);
        }
    }

    private int colorForLevel(float l) {
        if (l < 0.6f) return Color.parseColor("#4CAF50");   // verde: nível bom
        if (l < 0.85f) return Color.parseColor("#FFC107");  // amarelo: nível alto
        return Color.parseColor("#F44336");                 // vermelho: perto de saturar
    }
}
