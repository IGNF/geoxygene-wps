package fr.ign.cogit.process.geoxygene.ppio;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.log4j.Logger;
import org.geoserver.wps.ppio.CDataPPIO;

import fr.ign.parameters.Parameters;


/**
 * 
 * @author MDVan-Damme
 */
public class ParametersNetworkMatchingPPIO extends CDataPPIO {

  /** LOGGER. */
  private final static Logger LOGGER = Logger.getLogger(ParametersNetworkMatchingPPIO.class.getName());

  /**
   * Default constructor.
   */
  protected ParametersNetworkMatchingPPIO() {
    super(Parameters.class, Parameters.class, "text/xml");
  }

  @Override
  public void encode(Object value, OutputStream os) throws IOException {
    throw new UnsupportedOperationException("Unsupported encode(Object, OutputStream) operation.");
  }

  @Override
  public Object decode(InputStream input) throws Exception {
    throw new UnsupportedOperationException("Unsupported decode(InputStream) operation.");
  }

  @Override
  public Object decode(String inputXML) throws Exception {
    LOGGER.debug("DECODE PARAM XML");
    LOGGER.debug(inputXML);
    // InputStream inputXSD = ParametersNetworkMatchingPPIO.class.getResourceAsStream("/schema/ParametersNetworkMatching.xsd");
    Parameters p = Parameters.unmarshall(inputXML);
    return p;
  }

  @Override
  public String getFileExtension() {
    return "xml";
  }

}
