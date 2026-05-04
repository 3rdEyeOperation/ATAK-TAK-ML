
package com.atakmap.android.takml.plugin;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import com.atak.plugins.impl.AbstractPlugin;
import com.atak.plugins.impl.PluginContextProvider;
import com.atakmap.android.maps.MapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.takml.GenericImageRecognitionMapComponent;

import gov.tak.api.plugin.IServiceController;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import com.atakmap.coremap.log.Log;

public class GenericImageRecognitionLifecycle extends AbstractPlugin {

    public GenericImageRecognitionLifecycle(IServiceController serviceController) {
        super(serviceController, new PluginTemplateTool(serviceController.getService
                (PluginContextProvider.class).getPluginContext()), new GenericImageRecognitionMapComponent());
    }
}
