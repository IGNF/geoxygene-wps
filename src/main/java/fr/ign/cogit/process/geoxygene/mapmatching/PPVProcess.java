package fr.ign.cogit.process.geoxygene.mapmatching;

import org.apache.log4j.Logger;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import fr.ign.cogit.geoxygene.api.feature.IFeatureCollection;
import fr.ign.cogit.geoxygene.contrib.graphe.PPV;
import fr.ign.cogit.geoxygene.feature.DefaultFeature;
import fr.ign.cogit.geoxygene.feature.Population;
import fr.ign.cogit.geoxygene.util.conversion.GeOxygeneGeoToolsTypes;
import fr.ign.cogit.process.geoxygene.GeoxygeneProcess;

/**
 * Recalage d'un jeu de points sur la route la plus proche par la méthode du plus proche voisin.
 * 
 * @author Sofiane Kriat - ENSG
 */
@DescribeProcess(title = "RecalagePPV", description = "Recale des points suivant la méthode PPV")
public class PPVProcess implements GeoxygeneProcess {
	
	private static Logger LOGGER = Logger.getLogger(PPVProcess.class.getName());
  
  @DescribeResult(name = "POINTS2", description = "Points récalés")
  public SimpleFeatureCollection execute(
      @DescribeParameter(name = "points", description = "Points à recaler") SimpleFeatureCollection points,
      @DescribeParameter(name = "reseauRoutier", description = "Réseau routier de la zone") SimpleFeatureCollection reseauRoutier,
      @DescribeParameter(name = "distance", description = "distance maximale d'appariemment") double distance) {
   
    try {
    
      // Recupere la projection de points
      CoordinateReferenceSystem crs = points.getSchema().getCoordinateReferenceSystem();
  
      // Recalage
      IFeatureCollection<?> pointsARecaler = GeOxygeneGeoToolsTypes.convert2IFeatureCollection(points);
      IFeatureCollection<?> geoxReseauRoutier = GeOxygeneGeoToolsTypes.convert2IFeatureCollection(reseauRoutier);
      
      Population<DefaultFeature> pointsProjetes = PPV.run(pointsARecaler, geoxReseauRoutier, 
          false, distance);
    
      // Transforme le résultat 
      LOGGER.info("Avant Avant final = " + pointsProjetes.size());
      SimpleFeatureCollection pointsRecales = GeOxygeneGeoToolsTypes.convert2FeatureCollection(pointsProjetes, crs);
      LOGGER.info("Avant final = " + pointsRecales.size());
      
      // Retourne le résultat
      return pointsRecales;
    
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

}

