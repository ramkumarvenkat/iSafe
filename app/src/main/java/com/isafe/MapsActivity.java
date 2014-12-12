package com.isafe;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.isafe.location.BackgroundLocationService;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class MapsActivity extends FragmentActivity implements LocationListener {

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private Button helpButton;
    private ToggleButton startStopJourneyToggleButton;

    private LocationManager locationManager;
    private String provider;

    private Location lastLocationChange = null;

    private boolean actionBarToggleJourneyStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        helpButton = (Button) findViewById(R.id.button);
        startStopJourneyToggleButton = (ToggleButton) findViewById(R.id.toggle_button);
    }

    @Override
    protected void onResume() {
        super.onResume();

        initialise();
        if (locationManager != null) {
            locationManager.requestLocationUpdates(1000, 1000, new Criteria(), this, null);
        }

        actionBarToggleJourneyStarted = getToggledState();
        startStopJourneyToggleButton.setChecked(actionBarToggleJourneyStarted);
        setHelpButtonVisibility();
    }

    @Override
    protected void onPause() {
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (actionBarToggleJourneyStarted) {
            Intent intent = new Intent(this, BackgroundLocationService.class);
            stopService(intent);
        }

        super.onDestroy();
    }

    private void initialise() {
        boolean isConnected = checkNetworkConnectivity();
        if (isConnected) {
            initialiseLocationManager();
            initialiseProvider();
            setUpMapIfNeeded();
        }
    }

    private boolean checkNetworkConnectivity() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();

        if (!isConnected) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Please connect to the internet")
                    .setCancelable(false)
                    .setItems(new CharSequence[]{"Wi-Fi", "Network Data"}, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            if (which == 0) {
                                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                            } else {
                                startActivity(new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS));
                            }
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            MapsActivity.this.finish();
                        }
                    });
            AlertDialog alert = builder.create();
            alert.show();
        }

        return isConnected;
    }

    private void initialiseLocationManager() {
        if (locationManager == null) {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        }
    }

    private void initialiseProvider() {

        if(lastLocationChange != null) {
            provider = lastLocationChange.getProvider();
        }

        if(provider == null) {
            provider = locationManager.getBestProvider(new Criteria(), true);
        }

        if (provider == null) {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                provider = LocationManager.GPS_PROVIDER;
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                provider = LocationManager.NETWORK_PROVIDER;
            } else {
                //show alert message to enable GPS
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("Please enable GPS to enable us track your location")
                        .setCancelable(false)
                        .setPositiveButton("Enable", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                startActivity(intent);
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                MapsActivity.this.finish();
                            }
                        });
                AlertDialog alert = builder.create();
                alert.show();
            }
        }
    }

    private void setUpMapIfNeeded() {
        if (provider != null && mMap == null) {
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            if (mMap != null) {
                mMap.setMyLocationEnabled(true);
            }
        }
    }

    private Location getUserCurrentLocation() {
        Location location = locationManager.getLastKnownLocation(provider);
        if (location == null) {
            location = lastLocationChange;
        }
        return location;
    }

    public void updateLocationChange(Location location) {
        lastLocationChange = location;

        double lat = location.getLatitude();
        double lng = location.getLongitude();

        LatLng latLng = new LatLng(lat, lng);
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 15);
        mMap.animateCamera(cameraUpdate);

        mMap.addMarker(new MarkerOptions().position(latLng).title(getAddressFromLocation(location)));
    }

    private String getAddressFromLocation(Location location) {
        String address = location.getLatitude() + ":" + location.getLongitude();
        try {
            address = new GetAddressTask(this).execute(location).get();
        } catch (InterruptedException e) {
            Log.e("MapsActivity",
                    "InterruptedException in Geocoder.getFromLocation()");
            e.printStackTrace();
        } catch (ExecutionException e) {
            Log.e("MapsActivity",
                    "ExecutionException in Geocoder.getFromLocation()");
            e.printStackTrace();
        }
        return address;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void toggleStartStopJourney(View view) {
        actionBarToggleJourneyStarted = !actionBarToggleJourneyStarted;
        if (actionBarToggleJourneyStarted) {
            setHelpButtonVisibility();
            commitToggleState();

            Intent intent = new Intent(this, BackgroundLocationService.class);
            startService(intent);
        } else {
            setHelpButtonVisibility();
            commitToggleState();

            Intent intent = new Intent(this, BackgroundLocationService.class);
            stopService(intent);
        }
    }

    private void setHelpButtonVisibility() {
        if (actionBarToggleJourneyStarted) {
            helpButton.setVisibility(View.VISIBLE);
        } else {
            helpButton.setVisibility(View.GONE);
        }
    }

    private void commitToggleState() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(Constants.START_STOP_JOURNEY_TOGGLE_STATE.toString(), actionBarToggleJourneyStarted);
        editor.commit();
    }

    private boolean getToggledState() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        return preferences.getBoolean(Constants.START_STOP_JOURNEY_TOGGLE_STATE.toString(), false);
    }

    public void callForHelp(View view) {
        Location location = getUserCurrentLocation();
    }

    @Override
    public void onLocationChanged(Location location) {
        updateLocationChange(location);
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }

    private class GetAddressTask extends
            AsyncTask<Location, Void, String> {

        Context mContext;

        public GetAddressTask(Context context) {
            super();
            mContext = context;
        }

        @Override
        protected String doInBackground(Location... locations) {

            List<Address> addresses = null;
            if (locations != null && Geocoder.isPresent()) {
                // Get the current location from the input parameter list
                Location loc = locations[0];

                Geocoder geocoder = new Geocoder(getApplicationContext(), Locale.getDefault());
                try {
                    addresses = geocoder.getFromLocation(loc.getLatitude(),
                            loc.getLongitude(), 1);

                } catch (IOException e) {
                    Log.e("MapsActivity",
                            "IO Exception in Geocoder.getFromLocation()");
                    e.printStackTrace();
                }
            }

            // If the reverse geocode returned an address
            if (addresses != null && addresses.size() > 0) {
                // Get the first address
                Address address = addresses.get(0);
                /*
                 * Format the first line of address (if available),
                 * city, and country name.
                 */
                String addressText = String.format(
                        "%s, %s, %s, %s",
                        // If there's a street address, add it
                        address.getMaxAddressLineIndex() > 0 ?
                                address.getAddressLine(0) : "",
                        address.getMaxAddressLineIndex() > 1 ?
                                address.getAddressLine(1) : "",
                        // Locality is usually a city
                        address.getLocality(),
                        // The country of the address
                        address.getCountryName());

                return addressText;
            } else {
                return "No address found";
            }
        }

        @Override
        protected void onPostExecute(String address) {
            Toast.makeText(getApplicationContext(), "You are at : " + address.toString(), Toast.LENGTH_LONG).show();
        }
    }
}
