package com.quanglewangle.peter.rocloc.data;

import android.content.Context;
import android.os.Handler;

import com.quanglewangle.peter.rocloc.api.ApiService;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Repository {

    private static Repository instance;

    public static Repository getInstance(Context context) {
        if (instance == null) instance = new Repository(context.getApplicationContext());
        return instance;
    }

    public interface LookupCallback {
        void onResult(OperatorEntity entity, boolean fromCache);
        void onError(String error);
    }

    private final AppDatabase db;
    private final ApiService api;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public Repository(Context context) {
        db = AppDatabase.getInstance(context);
        api = new ApiService();
    }

    public void lookup(String callsign, boolean forceRefresh, LookupCallback callback, Handler mainHandler) {
        executor.execute(() -> {
            String upper = callsign.trim().toUpperCase();
            OperatorEntity cached = db.operatorDao().getByCallsign(upper);

            if (cached != null && !forceRefresh) {
                mainHandler.post(() -> callback.onResult(cached, true));
                return;
            }

            api.lookup(upper, new ApiService.Callback() {
                @Override
                public void onSuccess(OperatorEntity fresh) {
                    fresh.lastSynced = System.currentTimeMillis();
                    executor.execute(() -> db.operatorDao().insert(fresh));
                    mainHandler.post(() -> callback.onResult(fresh, false));
                }

                @Override
                public void onError(String error) {
                    if (cached != null) {
                        mainHandler.post(() -> callback.onResult(cached, true));
                    } else {
                        mainHandler.post(() -> callback.onError(error));
                    }
                }
            });
        });
    }

    public interface RecentCallback {
        void onResult(List<OperatorEntity> list);
    }

    public void getRecent(Handler mainHandler, RecentCallback callback) {
        executor.execute(() -> {
            List<OperatorEntity> list = db.operatorDao().getRecent();
            mainHandler.post(() -> callback.onResult(list));
        });
    }
}
