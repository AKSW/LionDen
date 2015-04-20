/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package io;

import helper.KBInfo;
import helper.LIMES;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.hp.hpl.jena.datatypes.RDFDatatype;
import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.util.FileManager;
import com.hp.hpl.jena.vocabulary.OWL;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;


/**
 * Checks and extract data from LIMES linking specifications (i.e.,
 * configuration files)
 * @TODO Restrictions for some subjects e.g. from a certain location.
 * @author ngonga
 */
public class RDFConfigReader extends ConfigReader implements RDFSpecs{
	private static Model configModel = ModelFactory.createDefaultModel();
	private Resource specsSubject;

	/**
	 * 
	 *@author sherif
	 */
	public RDFConfigReader() {
		super();
	}

	/**
	 * Read either the source or the dataset description
	 * @param kb
	 * @author sherif
	 */
	public void readKBDescription(Resource kb) {
		KBInfo kbinfo = null;
		if(configModel.contains(kb, RDF.type, LIMES.SourceDataset)) {
			kbinfo = sourceInfo;
		}else if(configModel.contains(kb, RDF.type, LIMES.TargetDataset)) {
			kbinfo = targetInfo;
		}else{
			logger.error("Either " + LIMES.SourceDataset + " or " + LIMES.TargetDataset + " type statement is missing");
			System.exit(1);
		}
		kbinfo.id = getObject(kb, RDFS.label, true).toString();;
		kbinfo.endpoint = getObject(kb, LIMES.endPoint, true).toString();
		RDFNode graph = getObject(kb, LIMES.graph, false);
		if(graph != null){
			kbinfo.graph = graph.toString();
		}
		for(RDFNode r : getObjects(kb, LIMES.restriction, false)){
			String restriction = r.toString();
			if (restriction.endsWith(".")) {
				restriction = restriction.substring(0, restriction.length() - 1);
			}
			kbinfo.restrictions.add(restriction);
		}
		for(RDFNode properity : getObjects(kb, LIMES.property, true)){
			processProperty(kbinfo, properity.toString());
		}
		kbinfo.pageSize = parseInt(getObject(kb, LIMES.pageSize, true).toString());
		kbinfo.var = getObject(kb, LIMES.variable, true).toString();
		RDFNode type = getObject(kb, LIMES.type, false);
		if(type != null){
			kbinfo.type = type.toString().toLowerCase();
		}
		kbinfo.prefixes = prefixes;
	}

	/**
	 * @param s
	 * @param p
	 * @param isMandatory if set the program exit in case o not found, 
	 * 			otherwise a null value returned
	 * @return the object o of triple (s, p, o) if exists, null otherwise
	 * @author sherif
	 */
	private static RDFNode getObject(Resource s, Property p, boolean isMandatory){
		StmtIterator statements = configModel.listStatements(s, p, (RDFNode) null);
		if(statements.hasNext()){
			return statements.next().getObject();	
		}else{
			if(isMandatory){
				logger.error("Missing mandatory property " + p + ", Exit with error.");
				System.exit(1);
			}
		}
		return null;
	}

	/**
	 * @param s
	 * @param p
	 * @param isMandatory if set the program exit in case o not found, 
	 * 			otherwise a null value returned
	 * @return Set of all objects o of triples (s, p, o) if exist, null otherwise
	 * @author sherif
	 */
	private static Set<RDFNode> getObjects(Resource s, Property p, boolean isMandatory){
		Set<RDFNode> result = new HashSet<>();
		StmtIterator statements = configModel.listStatements(s, p, (RDFNode) null);
		while(statements.hasNext()){
			result.add(statements.next().getObject());	
		}
		if(isMandatory && result.size() == 0){
			logger.error("Missing mandatory property: " + p + ", Exit with error.");
			System.exit(1);
		}else if(result.size() == 0){
			return null;
		}
		return result;
	}



	/**
	 * @param filePath path to RDF config file
	 * @return true if the filePath contains all mandatory properties
	 * @author sherif
	 */
	/* (non-Javadoc)
	 * @see de.uni_leipzig.simba.io.ConfigReader#validateAndRead(java.io.InputStream, java.lang.String)
	 */
	public boolean validateAndRead(String filePath) {
		return validateAndRead(readModel(filePath));
	}

	/**
	 * @param configurationModel
	 * @return true if the configurationModel contains all mandatory properties
	 * @author sherif
	 */
	public boolean validateAndRead(Model configurationModel){
		configModel = configurationModel;
		StmtIterator stats = configModel.listStatements(null, RDF.type, LIMES.LimesSpecs);
		if(stats.hasNext()){
			specsSubject = stats.next().getSubject();
		}else{
			logger.error("Missing " + LIMES.LimesSpecs + ", Exit with error.");
			System.exit(1);
		}
		sourceInfo = new KBInfo();
		targetInfo = new KBInfo();
		prefixes = (HashMap<String, String>) configModel.getNsPrefixMap();

		//1. 2. Source & Target information
		readKBDescription((Resource) getObject(specsSubject, LIMES.hasSource, true));
		readKBDescription((Resource) getObject(specsSubject, LIMES.hasTarget, true));

		//3.METRIC
		Resource metric = (Resource) getObject(specsSubject, LIMES.hasMetric, true);
		metricExpression = getObject(metric, LIMES.expression, true).toString();

		//4. Number of exemplars
		RDFNode ex = getObject(specsSubject, LIMES.exemplars, false);
		if(ex != null){
			exemplars = Integer.parseInt(ex.toString());
		}

		//5. ACCEPTANCE file and conditions
		Resource acceptance = (Resource) getObject(specsSubject, LIMES.hasAcceptance, true);
		acceptanceThreshold = parseDouble(getObject(acceptance, LIMES.threshold, true).toString());
		acceptanceFile = getObject(acceptance, LIMES.file, true).toString();
		acceptanceRelation = getObject(acceptance, LIMES.relation, true).toString();

		//6. VERIFICATION file and conditions
		Resource review = (Resource) getObject(specsSubject, LIMES.hasReview, true);
		verificationThreshold = parseDouble(getObject(review, LIMES.threshold, true).toString());
		verificationFile = getObject(review, LIMES.file, true).toString();
		verificationRelation = getObject(review, LIMES.relation, true).toString();

		//7. EXECUTION plan
		RDFNode execution = getObject(specsSubject, LIMES.executionPlan, false);
		if(execution != null){
			executionPlan = execution.toString();
		}

		//8. TILING if necessary 
		RDFNode g = getObject(specsSubject, LIMES.granularity, false);
		if(g != null){
			granularity = Integer.parseInt(g.toString());
		}

		//9. OUTPUT format
		RDFNode output = getObject(specsSubject, LIMES.outputFormat, false);
		if(output != null){
			outputFormat = output.toString();
		}
		return true;
	}

	/**
	 * @param s
	 * @return
	 * @author sherif
	 */
	private double parseDouble(String s){
		if(s.contains("^")){
			s = s.substring(0, s.indexOf("^"));
		}
		if(s.contains("@")){
			s = s.substring(0, s.indexOf("@"));
		}
		return Double.parseDouble(s);
	}


	public Model xmlConfigToRDFConfig(String filePath, String specsFramWork){
		if(specsFramWork.equalsIgnoreCase("LIMES")){
			super.validateAndRead(filePath);
		}else if(specsFramWork.equalsIgnoreCase("SILK")){
			SILKConfigReader cr = new SILKConfigReader();
			cr.validateAndRead(filePath);
		}
		Model m = ModelFactory.createDefaultModel();
		String uri = LIMES.uri + sourceInfo.id.toLowerCase() + "TO" + targetInfo.id.toLowerCase();
		Resource s = ResourceFactory.createResource(uri); 
		m.add(s, RDF.type, LIMES.LimesSpecs);

		// 1. Source
		Resource source = ResourceFactory.createResource(uri + "Source");
		m.add(s, LIMES.hasSource, source);
		m.add(source, RDF.type, LIMES.SourceDataset);
		m.add(source, RDFS.label, sourceInfo.id);
		m.add(source, LIMES.endPoint, sourceInfo.endpoint);
		m.add(source, LIMES.variable, sourceInfo.var);
		m.add(source, LIMES.pageSize, sourceInfo.pageSize + "");
		for(String r : sourceInfo.restrictions){
			m.add(source, LIMES.restriction, ResourceFactory.createPlainLiteral(r));
		}
		for(String p : sourceInfo.properties){
			m.add(source, LIMES.property, ResourceFactory.createPlainLiteral(p));
		}

		// 2. Target
		Resource target = ResourceFactory.createResource(uri + "Target");
		m.add(s, LIMES.hasTarget, target);
		m.add(target, RDF.type, LIMES.TargetDataset);
		m.add(target, RDFS.label, targetInfo.id);
		m.add(target, LIMES.endPoint, targetInfo.endpoint+ "");
		m.add(target, LIMES.variable, targetInfo.var+ "");
		m.add(target, LIMES.pageSize, targetInfo.pageSize + "");
		for(String r : targetInfo.restrictions){
			m.add(target, LIMES.restriction, r);
		}
		for(String p : targetInfo.properties){
			m.add(target, LIMES.property, p);
		}

		// 3. Metric
		Resource metric = ResourceFactory.createResource(uri + "Metric");
		m.add(s, LIMES.hasMetric, metric);
		m.add(metric, RDF.type, LIMES.Metric);
		m.add(metric, LIMES.expression, metricExpression);

		//4. Number of exemplars
		m.add(s, LIMES.exemplars, exemplars + "");

		//5. ACCEPTANCE file and conditions
		Resource acceptance = ResourceFactory.createResource(uri + "Acceptance");
		m.add(s, LIMES.hasAcceptance, acceptance);
		m.add(acceptance, RDF.type, LIMES.Acceptance);
		m.add(acceptance, LIMES.threshold, acceptanceThreshold + "");
		m.add(acceptance, LIMES.file, acceptanceFile);
		m.add(acceptance, LIMES.relation, acceptanceRelation);

		//6. VERIFICATION file and conditions
		Resource review = ResourceFactory.createResource(uri + "Review");
		m.add(s, LIMES.hasReview, review);
		m.add(review, RDF.type, LIMES.Review);
		m.add(review, LIMES.threshold, verificationThreshold + "");
		m.add(review, LIMES.file, verificationFile);
		m.add(review, LIMES.relation, verificationRelation);

		//7. EXECUTION plan
		m.add(s, LIMES.executionPlan, executionPlan);

		//8. TILING if necessary 
		m.add(s, LIMES.granularity, granularity + "");

		//9. OUTPUT format
		if(outputFormat != null){
			m.add(s, LIMES.outputFormat, outputFormat);
		}

		// Prefixes
		m.setNsPrefixes(prefixes);
		m.setNsPrefix(LIMES.prefix, LIMES.uri);
		m.setNsPrefix("owl", OWL.NS);
		m.setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
		return m;
	}


	public Model xmlConfigToRDFConfigExtended(String filePath){
		super.validateAndRead(filePath);
		Model m = ModelFactory.createDefaultModel();
		String uri = LIMES.uri + filePath.substring(filePath.lastIndexOf("/"), filePath.lastIndexOf("."));
		Resource s = ResourceFactory.createResource(uri); 
		m.add(s, RDF.type, LIMES.LimesSpecs);

		// Prefixes
		m.setNsPrefixes(prefixes);
		m.setNsPrefix(LIMES.prefix, LIMES.uri);
		m.setNsPrefix("owl", OWL.NS);
		m.setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#");

		// 1. Source
		Resource source = ResourceFactory.createResource(uri + "_source");
		m.add(s, LIMES.hasSource, source);
		m.add(source, RDF.type, LIMES.SourceDataset);
		m.add(source, RDFS.label, sourceInfo.id);
		m.add(source, LIMES.endPoint, ResourceFactory.createResource(sourceInfo.endpoint));
		m.add(source, LIMES.variable, sourceInfo.var);
		m.add(source, LIMES.pageSize, ResourceFactory.createTypedLiteral(sourceInfo.pageSize));
		for(String r : sourceInfo.restrictions){
			m.add(source, LIMES.restriction, ResourceFactory.createPlainLiteral(r));
		}
		for(String p : sourceInfo.properties){
			m.add(source, LIMES.property, createResource(m, p));
		}

		// 2. Target
		Resource target = ResourceFactory.createResource(uri + "_target");
		m.add(s, LIMES.hasTarget, target);
		m.add(target, RDF.type, LIMES.TargetDataset);
		m.add(target, RDFS.label, targetInfo.id);
		m.add(target, LIMES.endPoint, ResourceFactory.createResource(targetInfo.endpoint));
		m.add(target, LIMES.variable, targetInfo.var+ "");
		m.add(target, LIMES.pageSize, ResourceFactory.createTypedLiteral(targetInfo.pageSize));
		for(String r : targetInfo.restrictions){
			m.add(target, LIMES.restriction, r);
		}
		for(String p : targetInfo.properties){
			m.add(target, LIMES.property, createResource(m, p));
		}

		// 3. Metric
		Resource metric = ResourceFactory.createResource(uri + "_metric");
		m.add(s, LIMES.hasMetric, metric);
		m.add(metric, RDF.type, LIMES.Metric);
		m.add(metric, LIMES.expression, metricExpression);

		//4. Number of exemplars
		if(exemplars > 0){
			m.add(s, LIMES.exemplars, exemplars + "");
		}

		//5. ACCEPTANCE file and conditions
		Resource acceptance = ResourceFactory.createResource(uri + "_acceptance");
		m.add(s, LIMES.hasAcceptance, acceptance);
		m.add(acceptance, RDF.type, LIMES.Acceptance);
		m.add(acceptance, LIMES.threshold, ResourceFactory.createTypedLiteral(acceptanceThreshold));
		m.add(acceptance, LIMES.file, ResourceFactory.createResource(acceptanceFile));
		m.add(acceptance, LIMES.relation, createResource(m, acceptanceRelation));

		//6. VERIFICATION file and conditions
		Resource review = ResourceFactory.createResource(uri + "_review");
		m.add(s, LIMES.hasReview, review);
		m.add(review, RDF.type, LIMES.Review);
		m.add(review, LIMES.threshold, ResourceFactory.createTypedLiteral(verificationThreshold));
		m.add(review, LIMES.file, ResourceFactory.createResource(verificationFile));
		m.add(review, LIMES.relation, createResource(m, verificationRelation));

		//7. EXECUTION plan
		m.add(s, LIMES.executionPlan, executionPlan);

		//8. TILING if necessary 
		m.add(s, LIMES.granularity, ResourceFactory.createTypedLiteral(granularity));

		//9. OUTPUT format
		if(outputFormat != null){
			m.add(s, LIMES.outputFormat, outputFormat);
		}
		return m;
	}

	private Resource createResource(Model m, String p) {
		if(p.contains(":")){
			String pPrefix = p.substring(0, p.indexOf(":"));
			if(!m.getNsPrefixMap().containsKey(pPrefix)){
				logger.error("Undefined prefix " + pPrefix);
				System.exit(1);
			}
			String pPrefixUri = m.getNsPrefixMap().get(pPrefix);
			p = p.replace(":", "").replace(pPrefix, pPrefixUri);
		}
		return ResourceFactory.createResource(p);
	}

	/**
	 * @param s
	 * @return
	 * @author sherif
	 */
	private int parseInt(String s){
		if(s.contains("^")){
			s = s.substring(0, s.indexOf("^"));
		}
		if(s.contains("@")){
			s = s.substring(0, s.indexOf("@"));
		}
		return Integer.parseInt(s);
	}


	/**
	 * read RDF model from file/URL
	 * @param fileNameOrUri
	 * @return
	 * @author sherif
	 */
	public static Model readModel(String fileNameOrUri)
	{
		long startTime = System.currentTimeMillis();
		Model model=ModelFactory.createDefaultModel();
		java.io.InputStream in = FileManager.get().open( fileNameOrUri );
		if (in == null) {
			throw new IllegalArgumentException(fileNameOrUri + " not found");
		}
		if(fileNameOrUri.contains(".ttl") || fileNameOrUri.contains(".n3")){
			logger.info("Opening Turtle file");
			model.read(in, null, "TTL");
		}else if(fileNameOrUri.contains(".rdf")){
			logger.info("Opening RDFXML file");
			model.read(in, null);
		}else if(fileNameOrUri.contains(".nt")){
			logger.info("Opening N-Triples file");
			model.read(in, null, "N-TRIPLE");
		}else{
			logger.info("Content negotiation to get RDFXML from " + fileNameOrUri);
			model.read(fileNameOrUri);
		}
		logger.info("Loading " + fileNameOrUri + " is done in " + (System.currentTimeMillis()-startTime) + "ms.");
		return model;
	}

	public  static List<String> getSpecsFiles(String specsPaths)
	{
		List<String> specFiles =  new ArrayList<String>();
		File folder = new File(specsPaths);
		File[] listOfFiles = folder.listFiles();

		for (int i = 0; i < listOfFiles.length; i++) 
		{
			if (listOfFiles[i].isFile())
			{
				if(listOfFiles[i].getName().endsWith(".xml"))
					specFiles.add(listOfFiles[i].getAbsolutePath());
			}
			else if (listOfFiles[i].isDirectory()) 
				specFiles.addAll(getSpecsFiles(specsPaths+"/"+listOfFiles[i].getName()));
		}
		return specFiles;
	}

	/**
	 * @param args
	 * @author sherif
	 */
	public static void main(String args[]) {
		RDFConfigReader cr = new RDFConfigReader(); //ConfigReaderSILK
		//		String file = "/home/sherif/JavaProjects/LIMES/Examples/acm_dblp.ttl";
		//		cr.validateAndRead(file);
		long starTime = System.currentTimeMillis();
		FileWriter fileWriter;
		try {
			logger.info("read file: " + args[0]);
			String outputFile = args[0].substring(0, args[0].lastIndexOf(".")) + ".ttl";
			fileWriter = new FileWriter(outputFile);
			try {
				cr.xmlConfigToRDFConfigExtended(args[0]).write(fileWriter, "TTL");

				cr.xmlConfigToRDFConfigExtended(args[0]).write(System.out, "TTL");
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			logger.info("Done in " + (System.currentTimeMillis() - starTime) + "ms");
			logger.info("Converted file saved to " + outputFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
		//		generateSpecs(args[0]);

	}

	/**
	 * 
	 * @author sherif
	 */
	private static void generateSpecs(String folder) {
		String specsSourceFolder =folder;
		//String specsTargetFolder = args[1];
		List<String> specFiles =null;
		try
		{
			specFiles = getSpecsFiles(specsSourceFolder);
		} catch (Exception e)
		{
			logger.error("problem in loading files\n"+e.getMessage());
			System.exit(1);
		}
		System.out.println("Number of files = "+ specFiles.size());
		RDFConfigReader cr = new RDFConfigReader(); //ConfigReaderSILK
		long starTime = System.currentTimeMillis();
		FileWriter fileWriter;
		for (String specFile : specFiles) {
			try {
				logger.info("read file: " + specFile);
				String outputFile = specFile.substring(0, specFile.lastIndexOf(".")) + ".ttl";
				fileWriter = new FileWriter(outputFile);
				Model m=null;
				try {
					m = cr.xmlConfigToRDFConfigExtended(specFile);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				m.write(fileWriter, "TTL");
				logger.info("Done in " + (System.currentTimeMillis() - starTime) + "ms");
				logger.info("Converted file saved to " + outputFile);
			} catch (Exception e) { System.out.println(e.getMessage());
			}
		}
	}
}
