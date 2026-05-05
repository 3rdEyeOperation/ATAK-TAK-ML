
package com.atakmap.android.takml.plugin;

import com.atak.plugins.impl.AbstractPlugin;
import com.atak.plugins.impl.PluginContextProvider;
import com.atakmap.android.takml.GenericImageRecognitionMapComponent;

import gov.tak.api.plugin.IServiceController;

public class GenericImageRecognitionLifecycle extends AbstractPlugin {

    public GenericImageRecognitionLifecycle(IServiceController serviceController) {
        super(serviceController, new PluginTemplateTool(serviceController.getService
                (PluginContextProvider.class).getPluginContext()), new GenericImageRecognitionMapComponent());
    }
}
