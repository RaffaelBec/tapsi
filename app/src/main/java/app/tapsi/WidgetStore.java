package app.tapsi;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Calendar;

/**
 * What the home-screen widget needs to know, kept outside the web page so the
 * widget works while the app is closed:
 *  - "snapshot": the first five counters as the page last reported them
 *  - "pending":  taps made on the widget that the page has not collected yet
 */
final class WidgetStore {

    private static final String PREFS = "widget";
    private static final String KEY_SNAPSHOT = "snapshot";
    private static final String KEY_PENDING = "pending";
    private static final Object LOCK = new Object();
    private static final int ICON_PX = 128;

    private WidgetStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Midnight at the start of today, in the phone's time zone (same value the page uses). */
    static long startOfToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    static void saveSnapshot(Context context, String json) {
        synchronized (LOCK) {
            prefs(context).edit().putString(KEY_SNAPSHOT, json).commit();
        }
    }

    static JSONObject snapshot(Context context) {
        try {
            return new JSONObject(prefs(context).getString(KEY_SNAPSHOT, "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private static JSONArray pending(Context context) {
        try {
            return new JSONArray(prefs(context).getString(KEY_PENDING, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    static void addTap(Context context, String counterId) {
        synchronized (LOCK) {
            JSONArray taps = pending(context);
            try {
                taps.put(new JSONObject().put("i", counterId).put("t", System.currentTimeMillis()));
            } catch (JSONException ignored) {
                return;
            }
            prefs(context).edit().putString(KEY_PENDING, taps.toString()).commit();
        }
    }

    /** Hands the uncollected taps to the page and forgets them here. */
    static String takePending(Context context) {
        synchronized (LOCK) {
            String taps = prefs(context).getString(KEY_PENDING, "[]");
            prefs(context).edit().remove(KEY_PENDING).commit();
            return taps;
        }
    }

    /** Uncollected widget taps for one counter made today. */
    static int pendingToday(Context context, String counterId) {
        long since = startOfToday();
        JSONArray taps;
        synchronized (LOCK) {
            taps = pending(context);
        }
        int n = 0;
        for (int k = 0; k < taps.length(); k++) {
            JSONObject tap = taps.optJSONObject(k);
            if (tap != null && counterId.equals(tap.optString("i")) && tap.optLong("t") >= since) {
                n++;
            }
        }
        return n;
    }

    // --- imported pictures, saved small so the widget can show them ---

    private static File iconFile(Context context, String iconId) {
        if (iconId == null || !iconId.matches("[A-Za-z0-9]{1,40}")) {
            return null;
        }
        File dir = new File(context.getFilesDir(), "widget_icons");
        dir.mkdirs();
        return new File(dir, iconId + ".png");
    }

    static boolean hasIcon(Context context, String iconId) {
        File f = iconFile(context, iconId);
        return f != null && f.exists();
    }

    static Bitmap loadIcon(Context context, String iconId) {
        File f = iconFile(context, iconId);
        return (f != null && f.exists()) ? BitmapFactory.decodeFile(f.getPath()) : null;
    }

    static void saveIcon(Context context, String iconId, String dataUrl) {
        File f = iconFile(context, iconId);
        if (f == null || dataUrl == null) {
            return;
        }
        try {
            byte[] bytes = Base64.decode(dataUrl.substring(dataUrl.indexOf(',') + 1), Base64.DEFAULT);
            Bitmap full = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (full == null) {
                return;
            }
            Bitmap small = Bitmap.createScaledBitmap(full, ICON_PX, ICON_PX, true);
            FileOutputStream out = new FileOutputStream(f);
            try {
                small.compress(Bitmap.CompressFormat.PNG, 100, out);
            } finally {
                out.close();
            }
        } catch (Exception ignored) {
            // The widget falls back to a placeholder for this counter.
        }
    }
}
