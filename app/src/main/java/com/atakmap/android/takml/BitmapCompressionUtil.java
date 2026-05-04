package com.atakmap.android.takml;

import android.graphics.Bitmap;

public class BitmapCompressionUtil {
    /**
     * Method to scale the Bitmap to respect the max bytes
     *
     * @param input    the Bitmap to scale if too large
     * @param maxBytes the amount of bytes the Image may be
     *
     * @return The scaled bitmap or the input if already valid
     */
    public static Bitmap scaleBitmap(final Bitmap input, final long maxBytes) {
        final int currentWidth = input.getWidth();
        final int currentHeight = input.getHeight();
        final int currentPixels = currentWidth * currentHeight;
        // Get the amount of max pixels:
        // 1 pixel = 4 bytes (R, G, B, A)
        final long maxPixels = maxBytes / 4; // Floored
        if (currentPixels <= maxPixels) {
            // Already correct size:
            return input;
        }
        // Scaling factor when maintaining aspect ratio is the square root since x and y have a relation:
        final double scaleFactor = Math.sqrt(maxPixels / (double) currentPixels);
        final int newWidthPx = (int) Math.floor(currentWidth * scaleFactor);
        final int newHeightPx = (int) Math.floor(currentHeight * scaleFactor);
        return Bitmap.createScaledBitmap(input, newWidthPx, newHeightPx, true);
    }

    /**
     * Method to scale the Bitmap to max byte of 0.5 mb. Parcelables
     * have a limit of 1mb for it's Binder transaction buffer
     *
     * @param input    the Bitmap to scale if too large
     *
     * @return The scaled bitmap or the input if already valid
     */
    public static Bitmap scaleBitmapForParcelable(final Bitmap input){
        long mb = 1024 * 1024;
        return scaleBitmap(input, mb / 4);
    }
}
