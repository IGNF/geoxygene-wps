package fr.ign.cogit.process.geoxygene;

import org.geoserver.wps.process.ProcessSelector;
import org.opengis.feature.type.Name;

/**
 * 
 * @author MDVan-Damme
 *
 */
public class GeoxygeneFilter extends ProcessSelector {
    
    @Override
    protected boolean allowProcess(Name processName) {
        if (!processName.getNamespaceURI().equals("JTS")) {
            return true;
        } else {
            return false;
        }
    }

}
