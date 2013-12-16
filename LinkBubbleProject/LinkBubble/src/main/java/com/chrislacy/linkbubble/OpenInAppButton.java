package com.chrislacy.linkbubble;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import java.util.List;

public class OpenInAppButton extends FrameLayout {

    private static final int NUM_ITEMS_IN_PREVIEW = 2;
    List<ContentView.AppForUrl> mAppsForUrl;
    private PreviewItemDrawingParams mParams = new PreviewItemDrawingParams(0, 0, 0, 0);
    private int mAppStackPadding;
    private int mAppStackPreviewSize;

    private int mIntrinsicIconSize;
    private float mBaselineIconScale;
    private int mBaselineIconSize;
    private int mAvailableSpaceInPreview;
    private int mPreviewXOffset;
    private int mPreviewYOffset;
    private float mMaxPerspectiveShift;

    // The amount of vertical spread between items in the stack [0...1]
    private static final float PERSPECTIVE_SHIFT_FACTOR = 0.24f;

    // The degree to which the item in the back of the stack is scaled [0...1]
    // (0 means it's not scaled at all, 1 means it's scaled to nothing)
    private static final float PERSPECTIVE_SCALE_FACTOR = 0.35f;



    public OpenInAppButton(Context context) {
        this(context, null);
    }

    public OpenInAppButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public OpenInAppButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        Resources resources = context.getResources();
        mAppStackPreviewSize = resources.getDimensionPixelSize(R.dimen.toolbar_height);
        mAppStackPadding = resources.getDimensionPixelSize(R.dimen.app_stack_padding);
        mPreviewXOffset = resources.getDimensionPixelSize(R.dimen.app_stack_x_offset);
        mPreviewYOffset = resources.getDimensionPixelSize(R.dimen.app_stack_y_offset);
    }

    boolean configure(List<ContentView.AppForUrl> appsForUrl) {
        mAppsForUrl = appsForUrl;
        int appsForUrlSize = appsForUrl != null ? appsForUrl.size() : 0;
        if (appsForUrlSize == 1) {
            ContentView.AppForUrl appForUrl = appsForUrl.get(0);
            Drawable d = appForUrl.getIcon(getContext());
            if (d != null) {
                setBackground(d);
                setVisibility(VISIBLE);
                setTag(appForUrl);
                return true;
            }
        } else if (appsForUrlSize > 1) {
            setVisibility(VISIBLE);
            setBackground(null);
            return true;
        }

        return false;
    }

    private void computePreviewDrawingParams(int drawableSize) {
        if (mIntrinsicIconSize != drawableSize) {
            mIntrinsicIconSize = drawableSize;

            computePreviewDrawingParams();
        }
    }

    void computePreviewDrawingParams() {
        final int previewSize = mAppStackPreviewSize;
        final int previewPadding = mAppStackPadding;

        mAvailableSpaceInPreview = (previewSize - 2 * previewPadding);
        // cos(45) = 0.707  + ~= 0.1) = 0.8f
        int adjustedAvailableSpace = (int) ((mAvailableSpaceInPreview / 2) * (1 + 0.8f));

        int unscaledHeight = (int) (mIntrinsicIconSize * (1 + PERSPECTIVE_SHIFT_FACTOR));
        mBaselineIconScale = (1.0f * adjustedAvailableSpace / unscaledHeight);

        mBaselineIconSize = (int) (mIntrinsicIconSize * mBaselineIconScale);
        mMaxPerspectiveShift = mBaselineIconSize * PERSPECTIVE_SHIFT_FACTOR;
    }

    private void computePreviewDrawingParams(Drawable d) {
        computePreviewDrawingParams(d.getIntrinsicWidth());
    }

    class PreviewItemDrawingParams {
        PreviewItemDrawingParams(float transX, float transY, float scale, int overlayAlpha) {
            this.transX = transX;
            this.transY = transY;
            this.scale = scale;
            this.overlayAlpha = overlayAlpha;
        }
        float transX;
        float transY;
        float scale;
        int overlayAlpha;
        Drawable drawable;
    }

    @Override
    protected void dispatchDraw(android.graphics.Canvas canvas) {
        super.dispatchDraw(canvas);

        if (mAppsForUrl.size() == 0) {
            return;
        }

        Context context = getContext();
        Drawable d = mAppsForUrl.get(0).getIcon(context);
        if (d != null) {
            computePreviewDrawingParams(d);
        }

        int nItemsInPreview = Math.min(mAppsForUrl.size(), NUM_ITEMS_IN_PREVIEW);
        for (int i = nItemsInPreview - 1; i >= 0; i--) {
            d = mAppsForUrl.get(i).getIcon(context);
            mParams = computePreviewItemDrawingParams(i, mParams);
            mParams.drawable = d;
            drawPreviewItem(canvas, mParams);
        }
    }

    private PreviewItemDrawingParams computePreviewItemDrawingParams(int index,
                                                                     PreviewItemDrawingParams params) {
        index = NUM_ITEMS_IN_PREVIEW - index - 1;
        float r = (index * 1.0f) / (NUM_ITEMS_IN_PREVIEW - 1);
        float scale = (1 - PERSPECTIVE_SCALE_FACTOR * (1 - r));

        float offset = (1 - r) * mMaxPerspectiveShift;
        float scaledSize = scale * mBaselineIconSize;
        float scaleOffsetCorrection = (1 - scale) * mBaselineIconSize;

        // We want to imagine our coordinates from the bottom left, growing up and to the
        // right. This is natural for the x-axis, but for the y-axis, we have to invert things.

        //float cellScale = preferences.getWorkspaceCellScale();
        float previewXOffset = mPreviewXOffset;
        float previewYOffset = mPreviewYOffset;

        float transY = mAvailableSpaceInPreview - (offset + scaledSize + scaleOffsetCorrection) + previewYOffset;
        float transX = offset + scaleOffsetCorrection + previewXOffset;
        float totalScale = mBaselineIconScale * scale;
        final int overlayAlpha = (int) (80 * (1 - r));

        if (params == null) {
            params = new PreviewItemDrawingParams(transX, transY, totalScale, overlayAlpha);
        } else {
            params.transX = transX;
            params.transY = transY;
            params.scale = totalScale;
            params.overlayAlpha = overlayAlpha;
        }
        return params;
    }

    private void drawPreviewItem(Canvas canvas, PreviewItemDrawingParams params) {
        canvas.save();
        canvas.translate(params.transX, params.transY);
        canvas.scale(params.scale, params.scale);
        Drawable d = params.drawable;

        if (d != null) {
            d.setBounds(0, 0, mIntrinsicIconSize, mIntrinsicIconSize);
            d.setFilterBitmap(true);
            d.setColorFilter(Color.argb(params.overlayAlpha, 0, 0, 0), PorterDuff.Mode.SRC_ATOP);
            d.draw(canvas);
            d.clearColorFilter();
            d.setFilterBitmap(false);
        }
        canvas.restore();
    }

}
