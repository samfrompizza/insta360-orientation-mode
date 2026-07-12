package com.arashivision.sdk.demo.view.discretescrollview;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

public class FadingEdgeDecoration extends RecyclerView.ItemDecoration {

    private final float mFadingPercent = 0.35f;
    private final int mEdgeColor = Color.TRANSPARENT;
    private final int mCenterColor = Color.parseColor("#E6000000");

    private final Paint mPaint = new Paint();
    private int mLayerId;

    @Override
    public void onDrawOver(Canvas canvas, RecyclerView parent, RecyclerView.State state) {
        super.onDrawOver(canvas, parent, state);
        mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        // 左侧蒙版
        mPaint.setShader(new LinearGradient(
                0f,
                0f,
                parent.getWidth() * mFadingPercent,
                0f,
                mEdgeColor,
                mCenterColor,
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(
                0f,
                0f,
                parent.getWidth() * mFadingPercent,
                parent.getBottom(),
                mPaint
        );
        // 右侧蒙版
        mPaint.setShader(new LinearGradient(
                parent.getWidth() * (1 - mFadingPercent),
                0f,
                parent.getWidth(),
                0f,
                mCenterColor,
                mEdgeColor,
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(
                parent.getWidth() * (1 - mFadingPercent),
                0f,
                parent.getWidth(),
                parent.getBottom(),
                mPaint
        );
        mPaint.setXfermode(null);
        canvas.restoreToCount(mLayerId);
    }

    @Override
    public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
        super.onDraw(c, parent, state);
        mLayerId = c.saveLayer(
                0.0f,
                0.0f,
                parent.getWidth(),
                parent.getHeight(),
                mPaint
        );
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        super.getItemOffsets(outRect, view, parent, state);
    }
}
