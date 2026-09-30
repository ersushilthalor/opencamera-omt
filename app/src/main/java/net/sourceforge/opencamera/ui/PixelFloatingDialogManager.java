package net.sourceforge.opencamera.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.PreferenceKeys;
import net.sourceforge.opencamera.R;
import net.sourceforge.opencamera.cameracontroller.CameraController;
import net.sourceforge.opencamera.preview.Preview;
import net.sourceforge.opencamera.preview.VideoProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages modern Google Pixel-styled floating dialog windows for selectable settings
 * such as Video Resolution, Photo Resolution, Timer, etc.
 */
public class PixelFloatingDialogManager {

    public interface OnOptionSelectedListener {
        void onOptionSelected(int index, String value, String title);
    }

    /**
     * Helper to hook any ListPreference to open as a modern floating popup window
     * instead of navigating or opening legacy dialogs.
     */
    public static void hookListPreferenceWithFloatingDialog(final Activity activity, final ListPreference lp, final String subtitle) {
        if (activity == null || lp == null) return;
        lp.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final CharSequence[] curEntries = lp.getEntries();
                final CharSequence[] curValues = lp.getEntryValues();
                if (curEntries == null || curEntries.length == 0) return false;
                String curVal = lp.getValue();
                String title = lp.getTitle() != null ? lp.getTitle().toString() : "Select Option";
                showFloatingDialog(
                        activity,
                        title,
                        subtitle,
                        curEntries,
                        curValues,
                        curVal,
                        new OnOptionSelectedListener() {
                            @Override
                            public void onOptionSelected(int index, String value, String entryTitle) {
                                lp.setValue(value);
                                if (lp.getOnPreferenceChangeListener() != null) {
                                    lp.getOnPreferenceChangeListener().onPreferenceChange(lp, value);
                                }
                                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
                                sp.edit().putString(lp.getKey(), value).apply();
                            }
                        }
                );
                return true;
            }
        });
    }

    /**
     * Show a generic modern floating dialog over the screen.
     */
    public static Dialog showFloatingDialog(Context context, String title, String subtitle,
                                            CharSequence[] entries, CharSequence[] values,
                                            String currentValue, final OnOptionSelectedListener listener) {
        if (context == null || entries == null || entries.length == 0) {
            return null;
        }

        final Dialog dialog = new Dialog(context, R.style.PixelFloatingDialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.pixel_floating_settings_dialog, null);
        dialog.setContentView(dialogView);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.dimAmount = 0.6f;
            window.setAttributes(lp);
        }

        TextView titleView = dialogView.findViewById(R.id.floating_dialog_title);
        titleView.setText(title);

        TextView subtitleView = dialogView.findViewById(R.id.floating_dialog_subtitle);
        if (subtitle != null && !subtitle.isEmpty()) {
            subtitleView.setText(subtitle);
            subtitleView.setVisibility(View.VISIBLE);
        } else {
            subtitleView.setVisibility(View.GONE);
        }

        ImageButton closeBtn = dialogView.findViewById(R.id.floating_dialog_close);
        closeBtn.setOnClickListener(v -> dialog.dismiss());

        LinearLayout optionsContainer = dialogView.findViewById(R.id.floating_options_container);
        optionsContainer.removeAllViews();

        for (int i = 0; i < entries.length; i++) {
            final int index = i;
            final String val = (values != null && i < values.length) ? values[i].toString() : entries[i].toString();
            final String entryTitle = entries[i].toString();
            final boolean isSelected = (currentValue != null && currentValue.equals(val));

            View item = inflater.inflate(R.layout.pixel_floating_option_item, optionsContainer, false);
            TextView itemTitle = item.findViewById(R.id.option_item_title);
            TextView itemSubtitle = item.findViewById(R.id.option_item_subtitle);
            ImageView itemCheck = item.findViewById(R.id.option_item_check);

            // Split title and subtitle if it has format like "Title (Subtitle)"
            int parenIdx = entryTitle.indexOf('(');
            if (parenIdx > 0 && entryTitle.endsWith(")")) {
                itemTitle.setText(entryTitle.substring(0, parenIdx).trim());
                itemSubtitle.setText(entryTitle.substring(parenIdx + 1, entryTitle.length() - 1).trim());
                itemSubtitle.setVisibility(View.VISIBLE);
            } else {
                itemTitle.setText(entryTitle);
                itemSubtitle.setVisibility(View.GONE);
            }

            if (isSelected) {
                item.setBackgroundResource(R.drawable.pixel_floating_item_selected_bg);
                itemTitle.setTextColor(Color.parseColor("#121212"));
                itemSubtitle.setTextColor(Color.parseColor("#444444"));
                itemCheck.setVisibility(View.VISIBLE);
                itemCheck.setColorFilter(Color.parseColor("#121212"));
            } else {
                item.setBackgroundResource(R.drawable.pixel_floating_item_bg);
                itemTitle.setTextColor(Color.WHITE);
                itemSubtitle.setTextColor(Color.parseColor("#AAAAAA"));
                itemCheck.setVisibility(View.GONE);
            }

            item.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onOptionSelected(index, val, entryTitle);
                }
                dialog.dismiss();
            });

            optionsContainer.addView(item);
        }

        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        return dialog;
    }

    /**
     * Show Video Resolution floating dialog directly from camera viewfinder or settings.
     */
    public static Dialog showVideoResolutionDialog(final MainActivity activity) {
        if (activity == null || activity.getPreview() == null) return null;
        Preview preview = activity.getPreview();
        if (preview.getCameraController() == null) return null;

        String fps_value = activity.getApplicationInterface().getVideoFPSPref();
        List<String> video_quality = preview.getSupportedVideoQuality(fps_value);
        if (video_quality == null || video_quality.isEmpty()) {
            video_quality = preview.getVideoQualityHander().getSupportedVideoQuality();
        }
        if (video_quality == null || video_quality.isEmpty()) return null;

        CharSequence[] entries = new CharSequence[video_quality.size()];
        CharSequence[] values = new CharSequence[video_quality.size()];
        for (int i = 0; i < video_quality.size(); i++) {
            values[i] = video_quality.get(i);
            entries[i] = preview.getCamcorderProfileDescription(video_quality.get(i));
        }

        boolean is_high_speed = preview.fpsIsHighSpeed(fps_value);
        final String video_quality_preference_key = PreferenceKeys.getVideoQualityPreferenceKey(
                preview.getCameraId(),
                activity.getApplicationInterface().getCameraIdSPhysicalPref(),
                is_high_speed
        );

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
        String currentValue = sharedPreferences.getString(video_quality_preference_key, "");
        if (currentValue.isEmpty() && preview.getVideoQualityHander().getCurrentVideoQuality() != null) {
            currentValue = preview.getVideoQualityHander().getCurrentVideoQuality();
        }

        return showFloatingDialog(
                activity,
                "Video Resolution",
                "Choose capture resolution & frame format",
                entries,
                values,
                currentValue,
                new OnOptionSelectedListener() {
                    @Override
                    public void onOptionSelected(int index, String value, String title) {
                        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
                        sp.edit().putString(video_quality_preference_key, value).apply();
                        activity.getPreview().reopenCamera();
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                activity.getMainUI().updatePixelCameraUI();
                            }
                        });
                    }
                }
        );
    }

    /**
     * Show Photo Resolution floating dialog.
     */
    public static Dialog showPhotoResolutionDialog(final MainActivity activity) {
        if (activity == null || activity.getPreview() == null) return null;
        Preview preview = activity.getPreview();
        List<CameraController.Size> sizes = preview.getSupportedPictureSizes(false);
        if (sizes == null || sizes.isEmpty()) return null;

        CharSequence[] entries = new CharSequence[sizes.size()];
        CharSequence[] values = new CharSequence[sizes.size()];
        CameraController.Size currentSize = preview.getCurrentPictureSize();
        String currentValue = currentSize != null ? (currentSize.width + " " + currentSize.height) : "";

        for (int i = 0; i < sizes.size(); i++) {
            CameraController.Size s = sizes.get(i);
            values[i] = s.width + " " + s.height;
            entries[i] = s.width + " x " + s.height + " " + Preview.getAspectRatioMPString(activity.getResources(), s.width, s.height, false);
        }

        final String res_pref_key = PreferenceKeys.getResolutionPreferenceKey(
                preview.getCameraId(),
                activity.getApplicationInterface().getCameraIdSPhysicalPref()
        );

        return showFloatingDialog(
                activity,
                "Photo Resolution",
                "Select image size and aspect ratio",
                entries,
                values,
                currentValue,
                new OnOptionSelectedListener() {
                    @Override
                    public void onOptionSelected(int index, String value, String title) {
                        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
                        sp.edit().putString(res_pref_key, value).apply();
                        activity.getPreview().reopenCamera();
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                activity.getMainUI().updatePixelCameraUI();
                            }
                        });
                    }
                }
        );
    }
}
