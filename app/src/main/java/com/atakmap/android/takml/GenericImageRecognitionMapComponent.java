
package com.atakmap.android.takml;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.dropdown.DropDownMapComponent;

import com.atakmap.android.takml.plugin.R;
import com.atakmap.android.takml.receivers.GenericImageRecognitionDropDownReceiver;
import com.atakmap.coremap.log.Log;

public class GenericImageRecognitionMapComponent
        extends DropDownMapComponent {

    public static final String TAG = GenericImageRecognitionMapComponent.class.getSimpleName();
    public Context pluginContext;

    public void onCreate(final Context context, Intent intent,
            final MapView view) {
        super.onCreate(context, intent, view);
        context.setTheme(R.style.ATAKPluginTheme);
        pluginContext = context;

        GenericImageRecognitionDropDownReceiver ddr = new GenericImageRecognitionDropDownReceiver(view, context);
        Log.d(TAG, "registering the plugin filter");
        DocumentedIntentFilter ddFilter = new DocumentedIntentFilter();
        ddFilter.addAction(GenericImageRecognitionDropDownReceiver.SHOW_PLUGIN);
        registerDropDownReceiver(ddr, ddFilter);
    }

    private void registerReceiverUsingPluginContext(Context pluginContext, DropDownReceiver rec, String actionName) {
        DocumentedIntentFilter mainIntentFilter = new DocumentedIntentFilter();
        mainIntentFilter.addAction(actionName);
        this.registerReceiver(pluginContext, rec, mainIntentFilter);
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        super.onDestroyImpl(context, view);
    }

}
