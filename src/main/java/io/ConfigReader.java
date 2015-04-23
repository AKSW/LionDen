/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package io;

import helper.KBInfo;

import java.io.*;

import org.w3c.dom.*;

import javax.xml.parsers.*;

import java.util.HashMap;

import org.apache.log4j.*;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Checks and extract data from LIMES linking specifications (i.e.,
 * configuration files)
 * @TODO Restrictions for some subjects e.g. from a certain location.
 * @author ngonga
 */
public class ConfigReader {

	public KBInfo sourceInfo;
	public KBInfo targetInfo;
	public String metricExpression;
	public String acceptanceRelation;
	public String verificationRelation;
	public double acceptanceThreshold;
	public String acceptanceFile;
	public double verificationThreshold;
	public String verificationFile;
	public int exemplars;
	public int blocking =100;
	public HashMap<String, String> prefixes;
	public String outputFormat;
	public String executionPlan;
	public int granularity;
	protected static Logger logger;
	public static String AS = " AS ";
	public static String RENAME = " RENAME ";
	private String folder = "";

	/**
	 * constructor
	 *
	 */
	public ConfigReader() {
		logger = Logger.getLogger("LIMES");
		prefixes = new HashMap<String, String>();
		exemplars = -1;
		executionPlan = "simple";
		granularity = 2;
	}

	public void afterPropertiesSet() {
		sourceInfo.afterPropertiesSet();
		targetInfo.afterPropertiesSet();

		sourceInfo.prefixes = prefixes;
		targetInfo.prefixes = prefixes;
	}

	public static void processProperty(KBInfo kbinfo, String property) {
		String function = "", propertyLabel = "", propertyRename = "";
		//no preprocessing nor renaming
		if (!property.contains(RENAME) && !property.contains(AS)) {
			propertyLabel = property;
			propertyRename = property;
		} else if (!property.contains(RENAME) && property.contains(AS)) {
			propertyLabel = property.substring(0, property.indexOf(AS));
			propertyRename = propertyLabel;
			function = property.substring(property.indexOf(AS) + AS.length(), property.length());
		} else if (!property.contains(AS) && property.contains(RENAME)) {
			propertyLabel = property.substring(0, property.indexOf(RENAME));
			propertyRename = property.substring(property.indexOf(RENAME) + RENAME.length(), property.length());
			function = null;
		} //property contains both AS and RENAME, in that order
		else {
			propertyLabel = property.substring(0, property.indexOf(AS));
			function = property.substring(property.indexOf(AS) + AS.length(), property.indexOf(RENAME));
			propertyRename = property.substring(property.indexOf(RENAME) + RENAME.length(), property.length());
		}

		//now ensure that we have a map for the given label
		if (!kbinfo.functions.containsKey(propertyLabel)) {
			kbinfo.functions.put(propertyLabel, new HashMap<String, String>());
		}

		kbinfo.functions.get(propertyLabel).put(propertyRename, function);

		//might be that the same label leads to two different propertydubs
		if (!kbinfo.properties.contains(propertyLabel)) {
			kbinfo.properties.add(propertyLabel);
		}
	}

	public void processKBDescription(String kb, NodeList children) {
		KBInfo kbinfo;
		if (kb.equalsIgnoreCase("source")) {
			kbinfo = sourceInfo;
		} else {
			kbinfo = targetInfo;
		}
		String property, split[];
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child.getNodeName().equals("ID")) {
				kbinfo.id = getText(child);
			} else if (child.getNodeName().equals("ENDPOINT")) {
				kbinfo.endpoint = getText(child);
			} else if (child.getNodeName().equals("GRAPH")) {
				kbinfo.graph = getText(child);
			} else if (child.getNodeName().equals("RESTRICTION")) {
				String restriction = getText(child).trim();
				if (restriction.endsWith(".")) {
					restriction = restriction.substring(0, restriction.length() - 1);
				}
				kbinfo.restrictions.add(restriction);
			} else if (child.getNodeName().equals("PROPERTY")) {
				property = getText(child);

				processProperty(kbinfo, property);

			} else if (child.getNodeName().equals("PAGESIZE")) {
				kbinfo.pageSize = Integer.parseInt(getText(child));
			} else if (child.getNodeName().equals("VAR")) {
				kbinfo.var = getText(child);
			} else if (child.getNodeName().equals("TYPE")) {
				kbinfo.type = getText(child);
			}
		}
		kbinfo.prefixes = prefixes;
	}

	/**
	 * Returns true if the input complies to the LIMES DTD and contains
	 * everything needed. NB: The path to the DTD must be specified in the input
	 * file
	 *
	 * @param input The input XML file as Stream
	 * @return true if parsing was successful, else false
	 */
	public boolean validateAndRead(InputStream input, String filePath) {
		//		DtdChecker dtdChecker = new DtdChecker();
		try {
			//            InputStream input = new FileInputStream(inputString);
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			//make sure document is valid
			factory.setValidating(true);
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document xmlDocument = builder.parse(input);

			//            if (dtdChecker.valid) {
			//now extract what we need
			//                logger.info("File is valid. Parsing ...");
			sourceInfo = new KBInfo();
			targetInfo = new KBInfo();
			//0. Prefixes
			NodeList list = xmlDocument.getElementsByTagName("PREFIX");
			NodeList children;
			String namespace = "", label = "";
			for (int i = 0; i < list.getLength(); i++) {
				children = list.item(i).getChildNodes();
				for (int j = 0; j < children.getLength(); j++) {
					Node child = children.item(j);
					if (child.getNodeName().equals("NAMESPACE")) {
						namespace = getText(child);
					} else if (child.getNodeName().equals("LABEL")) {
						label = getText(child);
					}
				}
				prefixes.put(label, namespace);
			}
			//1. Source information
			list = xmlDocument.getElementsByTagName("SOURCE");
			children = list.item(0).getChildNodes();
			processKBDescription("SOURCE", children);
			//                logger.info("Source = " + sourceInfo);
			//2. Target information
			list = xmlDocument.getElementsByTagName("TARGET");
			children = list.item(0).getChildNodes();
			processKBDescription("TARGET", children);
			//                logger.info("Target = " + targetInfo);
			//3.METRIC
			list = xmlDocument.getElementsByTagName("METRIC");
			metricExpression = getText(list.item(0));
			//4. Number of exemplars
			list = xmlDocument.getElementsByTagName("EXEMPLARS");
			if (list.getLength() > 0) {
				exemplars = Integer.parseInt(getText(list.item(0)));
				//                   logger.info("Computation will be carried out with " + exemplars + " exemplars");
			}
			//5. ACCEPTANCE file and conditions
			list = xmlDocument.getElementsByTagName("ACCEPTANCE");
			children = list.item(0).getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				Node child = children.item(i);
				if (child.getNodeName().equals("THRESHOLD")) {
					acceptanceThreshold = Double.parseDouble(getText(child));
				} else if (child.getNodeName().equals("FILE")) {
					String file = getText(child);
					acceptanceFile = file;
				} else if (child.getNodeName().equals("RELATION")) {
					acceptanceRelation = getText(child);
				}
			}
			//                logger.info("Instances with similarity beyond " + acceptanceThreshold + " "
			//                        + "will be written in " + acceptanceFile + " and linked with " + acceptanceRelation);

			//6. VERIFICATION file and conditions
			list = xmlDocument.getElementsByTagName("REVIEW");
			children = list.item(0).getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				Node child = children.item(i);
				if (child.getNodeName().equals("THRESHOLD")) {
					verificationThreshold = Double.parseDouble(getText(child));
				} else if (child.getNodeName().equals("FILE")) {
					String file = getText(child);
					verificationFile = file;
				} else if (child.getNodeName().equals("RELATION")) {
					verificationRelation = getText(child);
				}
			}

			//7. EXECUTION plan
			if (list.getLength() > 0) {
				list = xmlDocument.getElementsByTagName("EXECUTION");
				children = list.item(0).getChildNodes();
				executionPlan = getText(list.item(0));
				//                    logger.info("Linking will be carried out by using the " + executionPlan + " execution plan");
			} else {
				//                    logger.info("Linking will be carried out by using the default execution plan");
			}
			//8. TILING if necessary
			list = xmlDocument.getElementsByTagName("GRANULARITY");
			if (list.getLength() > 0) {
				children = list.item(0).getChildNodes();
				granularity = Integer.parseInt(getText(list.item(0)));
				//                  logger.info("Linking will be carried by using granularity " + granularity);
			} else {
				//                  logger.info("Linking will be carried by using the default granularity.");
			}

			//9. OUTPUT format
			list = xmlDocument.getElementsByTagName("OUTPUT");
			if (list.getLength() > 0) {
				children = list.item(0).getChildNodes();
				outputFormat = getText(list.item(0));
				//                    logger.info("Output will be written in " + outputFormat + " format.");
			} else {
				//                   logger.info("Output will be written in N3 format.");
			}
			//                logger.info("Instances with similarity between " + verificationThreshold + " "
			//                        + "and " + acceptanceThreshold + " will be written in " + verificationFile
			//                        + " and linked with " + verificationRelation);

			//            }
		} catch (Exception e) {
			logger.warn(e.getMessage());
			e.printStackTrace();
			logger.warn("Some values were not set. Crossing my fingers and using defaults.");
		}
		//        logger.info("File " + input + " is valid.");

//		return dtdChecker.valid;
		return true;
	}

	/**
	 * Returns the content of a node
	 *
	 * @param node an item of the form <NODE> text </NODE>
	 * @return The text between <NODE> and </NODE>
	 */
	public static String getText(Node node) {

		// We need to retrieve the text from elements, entity
		// references, CDATA sections, and text nodes; but not
		// comments or processing instructions
		int type = node.getNodeType();
		if (type == Node.COMMENT_NODE
				|| type == Node.PROCESSING_INSTRUCTION_NODE) {
			return "";
		}

		StringBuffer text = new StringBuffer();

		String value = node.getNodeValue();
		if (value != null) {
			text.append(value);
		}
		if (node.hasChildNodes()) {
			NodeList children = node.getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				Node child = children.item(i);
				text.append(getText(child));
			}
		}

		return text.toString();

	}

	/**
	 * Returns true if the input complies to the LIMES DTD and contains
	 * everything needed. NB: The path to the DTD must be specified in the input
	 * file
	 *
	 * @param input The input XML file
	 * @return true if parsing was successful, else false
	 */
	public boolean validateAndRead(String filePath) {
		try {
			InputStream input = new FileInputStream(filePath);
			return validateAndRead(input, filePath);
		} catch (FileNotFoundException e) {
			logger.warn(e.getMessage());
			e.printStackTrace();
			logger.warn("Some values were not set. Crossing my fingers and using defaults.");
		}
		return false;
	}

	/**
	 * Returns config of sourceInfo knowledge base
	 *
	 * @return The KBInfo describing the sourceInfo knowledge base
	 */
	public KBInfo getSourceInfo() {
		return sourceInfo;
	}

	/**
	 * Returns config of targetInfo knowledge base
	 *
	 * @return The KBInfo describing the targetInfo knowledge base
	 */
	public KBInfo getTargetInfo() {
		return targetInfo;
	}

	public static void main(String args[]) {
		ConfigReader cr = new ConfigReader();
		String file = "Release_Examples/dblp-semanticwebresearcher.xml";
		cr.validateAndRead(file);
	}

	@Override
	public String toString() {
		return "ConfigReader [sourceInfo=" + sourceInfo + ", targetInfo="
				+ targetInfo + ", metricExpression=" + metricExpression
				+ ", acceptanceRelation=" + acceptanceRelation
				+ ", verificationRelation=" + verificationRelation
				+ ", acceptanceThreshold=" + acceptanceThreshold
				+ ", acceptanceFile=" + acceptanceFile
				+ ", verificationThreshold=" + verificationThreshold
				+ ", verificationFile=" + verificationFile + ", exemplars="
				+ exemplars + ", prefixes=" + prefixes + ", outputFormat="
				+ outputFormat + ", executionPlan=" + executionPlan
				+ ", granularity=" + granularity + ", logger=" + logger
				+ ", folder=" + folder + "]";
	}
}
