package app.tapsi;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Home-screen widget: the first five counters in one row with today's count
 * underneath. Tapping a counter adds one, without opening the app.
 */
public class CounterWidget extends AppWidgetProvider {

    private static final String ACTION_TAP = "app.tapsi.widget.TAP";
    private static final String ACTION_REFRESH = "app.tapsi.widget.REFRESH";
    private static final String EXTRA_ID = "id";
    private static final int SLOTS = 5;

    private static final int[] CELL = {R.id.cell0, R.id.cell1, R.id.cell2, R.id.cell3, R.id.cell4};
    private static final int[] EMOJI = {R.id.emoji0, R.id.emoji1, R.id.emoji2, R.id.emoji3, R.id.emoji4};
    private static final int[] IMG = {R.id.img0, R.id.img1, R.id.img2, R.id.img3, R.id.img4};
    private static final int[] COUNT = {R.id.count0, R.id.count1, R.id.count2, R.id.count3, R.id.count4};

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_TAP.equals(action)) {
            String id = intent.getStringExtra(EXTRA_ID);
            if (id != null) {
                WidgetStore.addTap(context, id);
            }
            refreshAll(context);
        } else if (ACTION_REFRESH.equals(action)) {
            refreshAll(context);
        } else {
            super.onReceive(context, intent);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] widgetIds) {
        manager.updateAppWidget(widgetIds, build(context));
        scheduleMidnightRefresh(context);
    }

    /** Redraws every placed widget from the saved state. */
    static void refreshAll(Context context) {
        Context app = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        int[] ids = manager.getAppWidgetIds(new ComponentName(app, CounterWidget.class));
        if (ids == null || ids.length == 0) {
            return;
        }
        manager.updateAppWidget(ids, build(app));
        scheduleMidnightRefresh(app);
    }

    private static RemoteViews build(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget);
        JSONObject snapshot = WidgetStore.snapshot(context);

        int background = Color.WHITE;
        try {
            background = Color.parseColor(snapshot.optString("bg", "#ffffff"));
        } catch (IllegalArgumentException ignored) {
            // keep white
        }
        int text = snapshot.optBoolean("light", true) ? 0xFF111111 : Color.WHITE;
        views.setInt(R.id.widget_bg, "setColorFilter", background);

        JSONArray items = snapshot.optJSONArray("items");
        int shown = items == null ? 0 : Math.min(items.length(), SLOTS);
        // Counts in the snapshot are for the day it was taken; after midnight they start at 0.
        boolean sameDay = snapshot.optLong("day", -1) == WidgetStore.startOfToday();

        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent openApp = PendingIntent.getActivity(context, 100,
                new Intent(context, MainActivity.class), flags);

        views.setViewVisibility(R.id.widget_empty, shown == 0 ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.widget_cells, shown == 0 ? View.GONE : View.VISIBLE);
        views.setTextColor(R.id.widget_empty, text);
        views.setOnClickPendingIntent(R.id.widget_empty, openApp);

        for (int k = 0; k < SLOTS; k++) {
            JSONObject item = k < shown ? items.optJSONObject(k) : null;
            if (item == null) {
                views.setViewVisibility(CELL[k], View.INVISIBLE);
                continue;
            }
            String id = item.optString("id");
            views.setViewVisibility(CELL[k], View.VISIBLE);

            Bitmap picture = item.has("c") ? WidgetStore.loadIcon(context, item.optString("c")) : null;
            if (picture != null) {
                views.setImageViewBitmap(IMG[k], picture);
                views.setViewVisibility(IMG[k], View.VISIBLE);
                views.setViewVisibility(EMOJI[k], View.GONE);
            } else {
                views.setTextViewText(EMOJI[k], item.optString("e", "❔"));
                views.setViewVisibility(EMOJI[k], View.VISIBLE);
                views.setViewVisibility(IMG[k], View.GONE);
            }

            int count = (sameDay ? item.optInt("n", 0) : 0) + WidgetStore.pendingToday(context, id);
            views.setTextViewText(COUNT[k], String.valueOf(count));
            views.setTextColor(COUNT[k], text);

            Intent tap = new Intent(context, CounterWidget.class).setAction(ACTION_TAP).putExtra(EXTRA_ID, id);
            views.setOnClickPendingIntent(CELL[k], PendingIntent.getBroadcast(context, k, tap, flags));
        }
        return views;
    }

    /** Counts reset each day, so redraw shortly after midnight even if nobody touches the phone. */
    private static void scheduleMidnightRefresh(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) {
            return;
        }
        Intent refresh = new Intent(context, CounterWidget.class).setAction(ACTION_REFRESH);
        PendingIntent pending = PendingIntent.getBroadcast(context, 200, refresh,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long nextMidnight = WidgetStore.startOfToday() + 24L * 60 * 60 * 1000 + 5000;
        alarms.set(AlarmManager.RTC, nextMidnight, pending);
    }
}
