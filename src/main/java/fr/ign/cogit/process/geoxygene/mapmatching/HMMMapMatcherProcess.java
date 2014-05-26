package fr.ign.cogit.process.geoxygene.mapmatching;

import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import fr.ign.cogit.geoxygene.api.feature.IFeature;
import fr.ign.cogit.geoxygene.api.feature.IFeatureCollection;
import fr.ign.cogit.geoxygene.api.feature.IPopulation;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IDirectPosition;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IDirectPositionList;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.ILineString;
import fr.ign.cogit.geoxygene.contrib.cartetopo.Arc;
import fr.ign.cogit.geoxygene.contrib.cartetopo.CarteTopo;
import fr.ign.cogit.geoxygene.contrib.cartetopo.Chargeur;
import fr.ign.cogit.geoxygene.contrib.graphe.ARM;
import fr.ign.cogit.geoxygene.feature.DefaultFeature;
import fr.ign.cogit.geoxygene.feature.FT_FeatureCollection;
import fr.ign.cogit.geoxygene.feature.Population;
import fr.ign.cogit.geoxygene.matching.hmmm.BDTopoMapMatcher;
import fr.ign.cogit.geoxygene.matching.hmmm.HMMMapMatcher.Node;
import fr.ign.cogit.geoxygene.schema.schemaConceptuelISOJeu.AttributeType;
import fr.ign.cogit.geoxygene.schema.schemaConceptuelISOJeu.FeatureType;
import fr.ign.cogit.geoxygene.spatial.geomprim.GM_Point;
import fr.ign.cogit.geoxygene.util.algo.JtsAlgorithms;
import fr.ign.cogit.geoxygene.util.conversion.GeOxygeneGeoToolsTypes;
import fr.ign.cogit.geoxygene.util.conversion.ShapefileReader;
import fr.ign.cogit.geoxygene.util.conversion.ShapefileWriter;
import fr.ign.cogit.geoxygene.util.index.Tiling;
import fr.ign.cogit.process.geoxygene.GeoxygeneProcess;

/**
 *
 *        This software is released under the licence CeCILL
 * 
 *        see Licence_CeCILL_V2-fr.txt
 *        see Licence_CeCILL_V2-en.txt
 * 
 *        see <a href="http://www.cecill.info/">http://www.cecill.info</a>
 * 
 * @copyright IGN
 * 
 * Recalage d'un jeu de points sur un réseau routier par la méthode "Hidden Markov Map Matching Through Noise and Sparseness".
 * 
 * @author R. Cuissard - ENSG
 * @author Sofiane Kriat - ENSG
 */
@DescribeProcess(title = "HMMMapMatcherProcess", description = "Recale des points suivant la méthode Hidden Markov Map Matching Through Noise and Sparseness")
public class HMMMapMatcherProcess implements GeoxygeneProcess {
	
	private static Logger LOGGER = Logger.getLogger(HMMMapMatcherProcess.class.getName());
	
	
	@DescribeResult(name = "POINTS2", description = "Points récalés")
	  public SimpleFeatureCollection execute(
			  @DescribeParameter(name = "points", description = "Points à recaler") SimpleFeatureCollection points,
			  @DescribeParameter(name = "reseauRoutier", description = "Réseau routier de la zone") SimpleFeatureCollection reseauRoutier,
			  @DescribeParameter(name = "distance", description = "distance maximale d'appariemment") double distance) {
		
		try {
			
			// Recupere la projection de points
			CoordinateReferenceSystem crs = points.getSchema().getCoordinateReferenceSystem();

			// Transformation
			IFeatureCollection<IFeature> pointsARecaler = (IFeatureCollection<IFeature>) GeOxygeneGeoToolsTypes.convert2IFeatureCollection(points);
			IFeatureCollection<IFeature> geoxReseauRoutier =(IFeatureCollection<IFeature>) GeOxygeneGeoToolsTypes.convert2IFeatureCollection(reseauRoutier);
			 
			// Calcul de l'arc ARM
			IFeatureCollection<Arc> armPop = armTraiteCouche(pointsARecaler,"");
			
			// Recalage
			Population<DefaultFeature> popMatchedPoints = graviMapMatcher(pointsARecaler,geoxReseauRoutier,distance,armPop);
			
			// Transformation 
			SimpleFeatureCollection pointsRecales = GeOxygeneGeoToolsTypes.convert2FeatureCollection(popMatchedPoints, crs);
			 
			// Retour
			return pointsRecales;
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
		
	}
	
	/**
	 * Calcul l'ARM d'une couche avec filtrage noeuds simples actif
	 * */
	private IFeatureCollection<Arc> armTraiteCouche(IFeatureCollection<IFeature> gravi, String nomLogger)
	{
		
		Logger logger= Logger.getLogger(nomLogger);
		
		logger.debug("Lecture de la couche gravi...");// Lecture de la couche gravi
		
		
		// ajout du z sur les points gravi à partir du h attributaire
		for (IFeature iFeature : gravi) {
			iFeature.getGeom().coord().get(0).setZ(new Double((Double)iFeature.getAttribute("H")));
		}
		logger.debug("nb de points = "+gravi.getElements().size());
		
		logger.debug("Creation de l'ARM... ");// Creation de l'arbre de recouvrement minimum...
		CarteTopo larbre = ARM.creeARM(gravi);		
		logger.debug("[OK] ");
		
		//////////////////////////////////////////////////////////////////////////////////
		// Filtrage noeuds simples : chaque branche de l'arbre est composé d'un seul arc
		// avec points intermédiaires (polyligne)		
		logger.debug("Filtrage noeuds simples... ");
		// Creation de la topologie pour info sommet ini / final
		// Tolérance = 0, puisque construction arc sur sommets
		larbre.creeTopologieArcsNoeuds(0);
		// On oriente tous les arcs en sens double
		IPopulation<Arc> lesArcs = larbre.getPopArcs();
		for (Arc arc : lesArcs)
		{
			arc.setOrientation(2);
		}
		larbre.filtreNoeudsSimples();
		logger.debug("[OK] ");
		///////////////////////Fin filtrage noeuds simples/////////////////////////////////
		
		
		return lesArcs;
	}
	
	
	/**
	 	* traiteCouche en cours de modification pour prise en compte du réseau dans la pondération
	*/
	private void armTraiteCouche2(String graviSHP,String armSHP, String bdtopoSHP, String nomLogger)
	{
		
		Logger logger= Logger.getLogger(nomLogger);
		
		logger.debug("Lecture de la couche gravi...");// Lecture de la couche gravi
		
		IPopulation<IFeature> gravi = ShapefileReader.read(graviSHP);
		
		logger.info("Lecture des donnees routieres ...");
	    IPopulation<IFeature> networkPop = ShapefileReader.read(bdtopoSHP,"routes", null, true);
	    logger.info("OK");
	    CarteTopo reseau = new CarteTopo("Reseau");
	    Chargeur.importClasseGeo(networkPop, reseau);
	    reseau.creeNoeudsManquants(0);
	    reseau.creeTopologieArcsNoeuds(0);
	    
	    
	   
		
		// ajout du z sur les points gravi à partir du h attributaire
		for (IFeature iFeature : gravi) {
			iFeature.getGeom().coord().get(0).setZ(new Double((Double)iFeature.getAttribute("H")));
			//listePts.add((IPoint)iFeature.getGeom());
		}
		logger.debug("nb de points = "+gravi.getElements().size());
		//charge le reseau A FAIRE
		// reseau.getPopArcs().initSpatialIndex(????) A faire: indexer la liste des arcs
		//reseau.projete(listePts, 100, 10); // ENLEVER????????????????????????????
		reseau.initialisePoids();
		
		
		logger.debug("Creation de l'ARM... ");// Creation de l'arbre de recouvrement minimum...
		CarteTopo larbre = ARM.creeARMPondere(gravi,reseau);		
		logger.debug("[OK] ");
		
		//////////////////////////////////////////////////////////////////////////////////
		// Filtrage noeuds simples : chaque branche de l'arbre est composé d'un seul arc
		// avec points intermédiaires (polyligne)		
		logger.debug("Filtrage noeuds simples... ");
		// Creation de la topologie pour info sommet ini / final
		// Tolérance = 0, puisque construction arc sur sommets
		larbre.creeTopologieArcsNoeuds(0);
		// On oriente tous les arcs en sens double
		IPopulation<Arc> lesArcs = larbre.getPopArcs();
		for (Arc arc : lesArcs)
		{
			arc.setOrientation(2);
		}
		larbre.filtreNoeudsSimples();
		logger.debug("[OK] ");
		///////////////////////Fin filtrage noeuds simples/////////////////////////////////
		
		logger.debug("Enregistrement des shp... ");// Enregistrement des couches au format shp...
		ShapefileWriter.write(lesArcs,armSHP);
		//ShapefileWriter.write(larbre.getPopNoeuds(),arm_sommets_SHP);		
		logger.debug("[OK] ");
	}
	
	/**
	 * Calcul du recalage de points par Map Matching
	 * variable gravi : la FeatureCollection de points à traiter
	 * variable bdT : la FeatureCollection de routes à utiliser
	 * variable distappa : la distance maximale de recalage ou d'appariemment
	 * variable deltaZOption : ajout d'un champ deltaZ pour connaissance du gain Z
	 * variable armPop L'ensemble des arcs de l'ARM formé par les routes
	 * */
	private Population<DefaultFeature> graviMapMatcher(IFeatureCollection<IFeature> gravi, IFeatureCollection<IFeature> bdT, 
			double distance, IFeatureCollection<Arc> armPop)
	{
		
		System.out.println("Algo 2");
		
	    IPopulation<IFeature> networkPop = (IPopulation<IFeature>) bdT;
	    System.out.println("OK");
	    
	    ////////// PREPARATION DES POINTS RECALES
	    // Création du type géométrique
	    FeatureType ftPoints = new FeatureType();
	    ftPoints.setGeometryType(GM_Point.class);
	    ftPoints.addFeatureAttribute(new AttributeType("id", "int"));
	    Population<DefaultFeature> popMatchedPoints = new Population<DefaultFeature>(
	        "Points Recales"); //$NON-NLS-1$
	    popMatchedPoints.setFeatureType(ftPoints);
	    popMatchedPoints.setClasse(DefaultFeature.class);
	    
	    // Parcours de toutes les polylignes de l'ARM...
	    Iterator<? extends IFeature> itLignes = armPop.iterator();
	    int cmp = 0;
	    BDTopoMapMatcher mapMatcher 	= null;
	    IFeature polyligneCourante 		= null;
	    FT_FeatureCollection<DefaultFeature> pseudoTrace= null;
	    IDirectPositionList listePoints	= null;
	    List<IDirectPosition> listePoints2= null;
	    Iterator<IDirectPosition> itPts = null;
	    Node result 					= null;
	    
	   
	    
	    GM_Point p = null;
	    ILineString l=null;
	    DefaultFeature projectedPoint = null;
	    
		// Creation de l'index spatial sur la bd topo...
	    System.out.println("Creation de l'index spatial du réseau routier...");
		if (!networkPop.hasSpatialIndex()) {
			networkPop.initSpatialIndex(Tiling.class, true, 20);
		}
		System.out.println("OK");
		
	    while (itLignes.hasNext())
	    {
	    	System.out.println("POLYLIGNE NUMERO : " + cmp);
	    	
		    polyligneCourante = itLignes.next();
		    
		    // Parcours de tous les points de la polyligne pour construire la trace pseudo trace
		    pseudoTrace = new FT_FeatureCollection<DefaultFeature>();
		    
		    listePoints = polyligneCourante.getGeom().coord();
		    listePoints2= listePoints.getList();
		    
		    itPts 	= listePoints2.iterator();
		    IDirectPosition pt = null;
		    DefaultFeature ft = null;
		    
		    System.out.println("NB PTS = "+listePoints2.size());
		    
		    while (itPts.hasNext())
		    {
		    	pt = (IDirectPosition)itPts.next();
		    	ft = new DefaultFeature(new GM_Point(pt));
		    	pseudoTrace.add(ft);
		    }
		    
			
		    mapMatcher = new BDTopoMapMatcher(pseudoTrace, networkPop, 100.0, 50.0, 6.0, distance);

		    System.out.println("Map Matching start with " + pseudoTrace.size());
		    result = mapMatcher.computeTransitions();
		    System.out.println("Map Matching finished with " + pseudoTrace.size());
		    
		    
		   
		    
		    // RECALAGE DES POINTS GPS SUR LE RESEAU
		    for (int i = 0; i < pseudoTrace.size(); i++)
		    {
		      p = (GM_Point) pseudoTrace.get(i).getGeom();
		      if (result.getStates() != null)
		      {
			      l = result.getStates().get(i).getGeometrie();
			      projectedPoint = popMatchedPoints.nouvelElement();
			      projectedPoint.setGeom(JtsAlgorithms.getClosestPoint(p.getPosition(), l).toGM_Point());
			      projectedPoint.setId(i);
			      pseudoTrace.get(i).setId(i);
		      }
		    }
			p 	= null;
			l	=null;
			projectedPoint = null;
			mapMatcher.getNetworkMap().nettoyer();
			mapMatcher 	= null;
		    polyligneCourante 		= null;
		    pseudoTrace = null;
		    listePoints	= null;
		    listePoints2= null;
		    itPts 		= null;
		    result 		= null;
		    // On force l'appel au garbage collector
		    System.gc();
		    cmp++;
		    
	    }
	    
	    
	    return popMatchedPoints;
	}

}
