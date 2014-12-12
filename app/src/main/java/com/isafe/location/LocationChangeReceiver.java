package com.isafe.location;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;

import com.google.android.gms.location.LocationClient;

/**
 * Created by rvenkataraman on 12/12/14.
 */
public class LocationChangeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        Location location = (Location) intent.getExtras().get(LocationClient.KEY_LOCATION_CHANGED);
        double lat = location.getLatitude();
        double lng = location.getLongitude();
        String provider = location.getProvider();
    }
}
