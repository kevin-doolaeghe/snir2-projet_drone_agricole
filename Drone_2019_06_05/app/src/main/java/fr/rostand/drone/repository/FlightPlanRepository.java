package fr.rostand.drone.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

import fr.rostand.drone.model.FlightPlan;
import fr.rostand.drone.util.VolleyRequestQueue;
import fr.rostand.drone.view.MainActivity;

public class FlightPlanRepository {
    private static final String TAG = "FlightPlanRepository";

    private static FlightPlanRepository instance;

    private Context mContext;
    private SharedPreferences mSharedPreferences;

    private FlightPlanRepository(Context context) {
        mContext = context;
        mSharedPreferences = context.getSharedPreferences(MainActivity.SHARED_PREFERENCES_ID, 0);
    }

    public static FlightPlanRepository getInstance(Context context) {
        if (instance == null) {
            instance = new FlightPlanRepository(context);
        }
        return instance;
    }

    public void addDataToFlightPlan(long latImgNb, long lonImgNb) {
        String baseUrl = mSharedPreferences.getString("baseUrl", VolleyRequestQueue.BASE_URL);
        long flightPlanId = mSharedPreferences.getLong("flightPlanId", 0);

        StringRequest request = new StringRequest(
                Request.Method.POST,
                baseUrl + "plan/add-data/" + flightPlanId,
                response -> {
                    Log.d(TAG, response);

                    FlightPlan flightPlan = new Gson().fromJson(response, FlightPlan.class);
                    Log.d(TAG, flightPlan.toString());
                },
                error -> {

                }) {
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String, String> params = new HashMap<>();
                params.put("latImgNb", Long.toString(latImgNb));
                params.put("lonImgNb", Long.toString(lonImgNb));

                return params;
            }
        };

        VolleyRequestQueue.getInstance(mContext).addToRequestQueue(request);
    }

    public void startAnalysis() {
        String baseUrl = mSharedPreferences.getString("baseUrl", VolleyRequestQueue.BASE_URL);
        long flightPlanId = mSharedPreferences.getLong("flightPlanId", 0);

        StringRequest request = new StringRequest(
                Request.Method.GET,
                baseUrl + "plan/start-synthesis/" + flightPlanId,
                response -> {
                    Log.d(TAG, response);
                },
                error -> {

                });

        VolleyRequestQueue.getInstance(mContext).addToRequestQueue(request);
    }
}