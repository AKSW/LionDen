package io;

import helper.KBInfo;
import helper.LIMES;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.sparql.util.NodeFactory;
import com.hp.hpl.jena.vocabulary.OWL;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;

/**
 * @author mofeed
 *
 */
public class SILKConfigReader extends ConfigReader implements RDFSpecs{
	
	private static Model configModel = ModelFactory.createDefaultModel();
	private static Model configModelSILK = ModelFactory.createDefaultModel();
	Document xmlDocument =null;
	private Resource specsSubject;
	private static Map<String,String> problematicFiles =null;
	private static String enrichFile ="";

	/**
	 * Returns true if the input complies to the LIMES DTD and contains
	 * everything needed. NB: The path to the DTD must be specified in the input
	 * file
	 * @author mofeed
	 * @param input The input XML file as Stream
	 * @return true if parsing was successful, else false
	 * @throws Exception 
	 */
	@Override
	public boolean validateAndRead(String file){
		try {
			InputStream input = new FileInputStream(file);
			initializeDOM4SILK(input);
			sourceInfo = new KBInfo();
			targetInfo = new KBInfo();
			processTag("Prefixes");
			processTag("DataSet");
			processTag("DataSource");
			processTag("LinkCondition");
			processTag("LinkType");
			processTag("Blocking");
			processTag("Output");
		} catch (Exception e) {
			logger.error("Some Tags were not set.");
			problematicFiles.put(file, e.getMessage());
		}
		//        logger.info("File " + input + " is valid.");
		return true;
	}


	/**
	 * @throws Exception
	 * @author mofeed
	 */
	public void processPrefixes() throws Exception
	{
		try{
			NodeList list = xmlDocument.getElementsByTagName("Prefixes");
			if(list.getLength() > 0 ){ // there are prefixes
				Node Prefixes = list.item(0); //Prefixes 'first occurence'
				list = Prefixes.getChildNodes(); // list of all prefixes
				int prefixesNr =list.getLength();
				for (int i=0;i< prefixesNr;i++){ // for each prefix
					Node prefix = list.item(i); 
					if (prefix.getNodeType() == Node.ELEMENT_NODE){ // for each prefix there is additional text element (empty one) which is bypassed
						prefixes.put(prefix.getAttributes().item(0).getNodeValue(),prefix.getAttributes().item(1).getNodeValue());
					}
				}
				sourceInfo.prefixes = prefixes;
				targetInfo.prefixes =prefixes;
			}
		}
		catch(Exception e){throw new Exception("processPrefixes: "+ e.getMessage());}
	}


	/**
	 * @param kbinfo
	 * @param dataSet
	 * @throws Exception
	 * @author mofeed
	 */
	public void processDataSets(KBInfo kbinfo, String dataSet) throws Exception
	{
		NodeList list = xmlDocument.getElementsByTagName(dataSet);
		if(list.getLength() > 1)
		{
			logger.error("Missed Tag -- "+dataSet);
			throw new Exception("problem in "+dataSet+" tag");
		}
		try{
			Node sourceDataset = list.item(0);
			NamedNodeMap sourceDataAttributes = sourceDataset.getAttributes();
			for(int i=0;i < sourceDataAttributes.getLength() ; i++ ){
				if(sourceDataAttributes.item(i).getNodeName().equalsIgnoreCase("datasource"))
					kbinfo.id = sourceDataAttributes.item(i).getNodeValue();
				else if(sourceDataAttributes.item(i).getNodeName().equalsIgnoreCase("var"))
					kbinfo.var = sourceDataAttributes.item(i).getNodeValue();
			}
			list = sourceDataset.getChildNodes(); //restrictions
			Node restriction =null;
			for(int i=0;i < list.getLength() ; i++ ){
				restriction = list.item(i);// restriction node
				if (restriction.getNodeType() == Node.ELEMENT_NODE)
					kbinfo.restrictions.add(restriction.getChildNodes().item(0).getNodeValue().trim().replaceAll("\t","").replaceAll("\n", " "));
			}
		}catch (Exception e){throw new Exception("processDataSets: "+ e.getMessage());}
	}


	/**
	 * @throws Exception
	 * @author mofeed
	 */
	public void processDataSource() throws Exception{
		NodeList list = xmlDocument.getElementsByTagName("DataSource");
		if(list.getLength() != 2){
			logger.error("Missed Tag -- DataSource");
			throw new Exception("problem in DataSource tag");
		}
		Node dataSource=null;
		for(int i=0;i < list.getLength() ; i++ ){
			dataSource = list.item(i);
			NamedNodeMap dataSourceAttributes = dataSource.getAttributes();
			if(dataSourceAttributes.getNamedItem("id").getNodeValue().equalsIgnoreCase(sourceInfo.id))
				processDataSourceParameters(sourceInfo, dataSource, dataSourceAttributes);
			else
				processDataSourceParameters(targetInfo, dataSource, dataSourceAttributes);
		}
	}
	/**
	 * @param 
	 * @return
	 * @author mofeed
	 */
	public void processDataSourceParameters(KBInfo kbInfo,Node dataSource ,NamedNodeMap dataSourceAttributes)
	{
		kbInfo.type =  dataSourceAttributes.getNamedItem("type").getNodeValue();
		NodeList parameters = dataSource.getChildNodes();
		NamedNodeMap parametersAttributes =null;
		for(int j=0; j< parameters.getLength() ; j++){
			if (parameters.item(j).getNodeType() == Node.ELEMENT_NODE){
				parametersAttributes = parameters.item(j).getAttributes();
				if(parametersAttributes.item(0).getNodeValue().equalsIgnoreCase("endpointuri"))
					kbInfo.endpoint = parametersAttributes.item(1).getNodeValue();
				else if(parametersAttributes.item(0).getNodeValue().equalsIgnoreCase("graph"))
					kbInfo.graph = parametersAttributes.item(1).getNodeValue();
				else if(parametersAttributes.item(0).getNodeValue().equalsIgnoreCase("pagesize"))
					kbInfo.pageSize = Integer.parseInt(parametersAttributes.item(1).getNodeValue());
			}
		}
	}
	/**
	 * @param 
	 * @return
	 * @author mofeed
	 */
	public void processLinkCondition() throws Exception{
		NodeList filter = xmlDocument.getElementsByTagName("Filter");
		Node filterThreshold = filter.item(0).getAttributes().getNamedItem("threshold");

		NodeList list = xmlDocument.getElementsByTagName("Compare");
		if(list.getLength() == 0){
			logger.error("Missed Tag -- Compare");
			throw new Exception("problem in Compare tag");

		}
		Map<String,String> compareParameters = new HashMap<String, String>();
		String aggregationFn = "";
		String expression = "";
		Map<String,String> expressions = new HashMap<String,String>();
		//List<String> metrics =  new ArrayList<String>();
		for(int i =0 ;i< list.getLength() ; i ++){
			Node compare = list.item(i);
			if (compare.getNodeType() == Node.ELEMENT_NODE){
				String metric = compare.getAttributes().getNamedItem("metric").getNodeValue();
				Node compareThreshold = compare.getAttributes().getNamedItem("threshold");
				NodeList inputs = compare.getChildNodes();
				for(int j=0; j<inputs.getLength();j++){
					Node input = inputs.item(j);
					if (input.getNodeType() == Node.ELEMENT_NODE){
						String path= inputs.item(j).getAttributes().getNamedItem("path").getNodeValue();
						String[] property = path.substring(1).split("/");
						if(property[0].equals(sourceInfo.var)){
							sourceInfo.properties.add(property[1]);
							expression = metric + "("+sourceInfo.var+"."+property[1]+",";
						}else{
							targetInfo.properties.add(property[1]);
							expression +=targetInfo.var+"."+property[1]+")";

						}
					}
				}
				String assignedThreshol="";
				if(compareThreshold!=null)
					assignedThreshol = compareThreshold.getNodeValue();
				else if(filterThreshold != null)
					assignedThreshol = filterThreshold.getNodeValue();
				expressions.put(expression,assignedThreshol);
			}

		}
		if(metricExpression==null)
			metricExpression="";
		if(list.getLength() > 1) // multiple compares
		{
			NodeList aggregateList = xmlDocument.getElementsByTagName("Aggregate");
			aggregationFn = aggregateList.item(0).getAttributes().item(0).getNodeValue(); // i.e. average
			metricExpression+=aggregationFn+"(";
			for (String expr : expressions.keySet()) {
				metricExpression+="("+expr+"|"+expressions.get(expr)+")";
			}
		}
		else{ // one compare
			for (String expr : expressions.keySet()) {
				metricExpression+=expr;
			}
		}
	}
	/**
	 * @param 
	 * @return
	 * @author mofeed
	 */
	public List<String> processTransformInput_Aux(Node transformInput)
	{
		///attributes
		NamedNodeMap transformInputAttributes = transformInput.getAttributes();
		String function ="";
		if(transformInputAttributes.getLength() > 0)
			function = transformInputAttributes.getNamedItem("function").getNodeValue(); // the only function can affect the form of input's "properties"
		///children
		NodeList list  = transformInput.getChildNodes();    	
		List<String> inputs =  new ArrayList<String>();
		for(int i=0;i<list.getLength();i++) //collect input
		{
			Node child = list.item(i); //an input
			if (child.getNodeType() == Node.ELEMENT_NODE){
				if(child.getNodeName().equalsIgnoreCase("input")){ // ignore param nodes
					String inp =child.getAttributes().getNamedItem("path").getNodeValue().replace("\\", "/");
					Node idTag = child.getAttributes().getNamedItem("id");
					if(idTag != null) // some versions use this exceptional format <Input path="?b" id="unnamed_2"></Input>
						inp+="/"+child.getAttributes().getNamedItem("id").getNodeValue();
					inputs.add(inp); //add input to list -- somes linkConditions use \ instead of / where the last the split character we use
				}
				else if(child.getNodeName().equalsIgnoreCase("transforminput"))
					inputs.addAll(processTransformInput_Aux(child));
			}
		}
		return inputs;
	}
	/**
	 * @param 
	 * @return
	 * @author mofeed
	 */
	public Map<String,String> processTransformInput(Node transformInput){
		List<String> inputs =  processTransformInput_Aux(transformInput);
		Map<String,String> processedInputs = new HashMap<String, String>();
		if(inputs.size()==1){ //one input
			String[] input= inputs.get(0).substring(1).split("/");
			processedInputs.put(input[0], input[1]);
		}
		else if(inputs.size()==2){
			String[] input1= inputs.get(0).substring(1).split("/");
			String[] input2= inputs.get(1).substring(1).split("/");
			if(input1[0].equals(input1[0])) //same variable => combine them
				processedInputs.put(input1[0], input1[1]+"/"+input2[1]);
			else{
				processedInputs.put(input1[0], input1[1]);
				processedInputs.put(input2[0], input2[1]);
			}
		}else
			logger.error("processTransformInput: More than one input in Transfor");
		return processedInputs;
	}


	/**
	 * @param compare
	 * @return
	 * @author mofeed
	 */
	public String processCompare(Node compare)
	{
		NodeList filter = xmlDocument.getElementsByTagName("Filter");
		Node filterThreshold = filter.item(0).getAttributes().getNamedItem("threshold");
		Node compareThreshold = compare.getAttributes().getNamedItem("threshold");

		NodeList list = compare.getChildNodes();
		Map<String, String> vars = new HashMap<String, String>(); 
		for(int i= 0 ;i <list.getLength();i++){
			Node compareChild = list.item(i);
			if (compareChild.getNodeType() == Node.ELEMENT_NODE){
				if(compareChild.getNodeName().equalsIgnoreCase("transforminput")){
					Map<String, String> x = processTransformInput(compareChild);
					vars.putAll(x);
				}
				else if(compareChild.getNodeName().equalsIgnoreCase("input")){
					String prop =  compareChild.getAttributes().getNamedItem("path").getNodeValue().substring(1).replace("\\","/"); // some formates use <Input path="?a\akt:has-author"/>
					String input[] = null;
					if(prop.contains("/<"))
						input= prop.split("/<");
					else
						input= prop.split("/");
					vars.put(input[0],input[1] );
				}
			}
		}
		String metric  = compare.getAttributes().getNamedItem("metric").getNodeValue();
		String expression =metric+"(";
		for (String var : vars.keySet()) {
			expression+=var+"."+vars.get(var)+",";
		}
		expression = expression.substring(0, expression.lastIndexOf(","));
		String assignedThreshol="";
		if(compareThreshold!=null)
			assignedThreshol = compareThreshold.getNodeValue();
		else if(filterThreshold != null)
			assignedThreshol = filterThreshold.getNodeValue();
		expression+="|"+assignedThreshol+")";
		return expression;
	}

	/**
	 * @param aggregate
	 * @return
	 * @author mofeed
	 */
	public String processAggregate(Node aggregate){
		String aggregateFn = aggregate.getAttributes().getNamedItem("type").getNodeValue();
		NodeList list = aggregate.getChildNodes();
		List<String>  compares =new ArrayList<String>();
		List<String>  aggregates =new ArrayList<String>();

		for(int i=0;i<list.getLength();i++){
			Node child = list.item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE){
				if(child.getNodeName().equalsIgnoreCase("compare"))
					compares.add( processCompare(child));
				else if(child.getNodeName().equalsIgnoreCase("aggregate"))
					aggregates.add(processAggregate(child));
			}
		}
		aggregateFn+="(";
		for (String c : compares) {
			aggregateFn+=c+",";
		}
		for (String a : aggregates) {
			aggregateFn+=a+",";
		}
		if(aggregateFn.endsWith(","))
			aggregateFn= aggregateFn.substring(0, aggregateFn.length()-1)+")";
		return aggregateFn;


	}
	/**
	 * @throws Exception
	 * @author mofeed
	 */
	public void processLinkConditionRec() throws Exception{
		try{
			NodeList list = xmlDocument.getElementsByTagName("LinkCondition"); 

			Node linkCondition = list.item(0);
			if(linkCondition == null)
				linkCondition = xmlDocument.getElementsByTagName("LinkageRule").item(0); //some versions use this tag instead
			list = linkCondition.getChildNodes();
			for(int i=0;i <list .getLength(); i++){
				Node child = list.item(i);
				if (child.getNodeType() == Node.ELEMENT_NODE){
					if(child.getNodeName().equalsIgnoreCase("compare")){
						metricExpression = processCompare(child); 
					}
					else if(child.getNodeName().equalsIgnoreCase("aggregate")){
						metricExpression =  processAggregate(child);
					}
				}
			}
		}
		catch(Exception e){throw new Exception("processLinkConditionRec: "+e.getMessage());}
		System.out.println(metricExpression);
	}


	/**
	 * 
	 * @author mofeed
	 */
	public void processLinkCondition2()
	{
		NodeList list = xmlDocument.getElementsByTagName("Compare");
		Map<String,HashMap<String, String>> comparesInputs = new HashMap<String, HashMap<String, String>>();
		Map<String,HashMap<String, String>> comparesParameters = new HashMap<String, HashMap<String, String>>();
		Map<String,HashMap<String, String>> comparesAttributes = new HashMap<String, HashMap<String, String>>();

		String aggregationFn = "";
		String totalExpression = "";
		Map<String,String> expressions = new HashMap<String,String>();
		int compareNr = list.getLength();
		HashMap<String,String> cAttributes =null;
		HashMap<String,String> cParameters =null;
		HashMap<String,String> cInputs =null;

		for(int i =0 ;i< compareNr ; i ++){
			Node compare = list.item(i);
			String compareId = "comapre"+i;
			if (compare.getNodeType() == Node.ELEMENT_NODE){
				//attributes i.e metric , threshold
				cAttributes = new HashMap<String,String>();
				for(int j=0;j<compare.getAttributes().getLength();j++)
					cAttributes.put(compare.getAttributes().item(j).getNodeName(), compare.getAttributes().item(j).getNodeValue());
				comparesAttributes.put(compareId, cAttributes);
				//inputs
				cInputs = new HashMap<String,String>();
				cParameters= new HashMap<String, String>();

				NodeList children = compare.getChildNodes();
				for(int j=0; j<children.getLength();j++){
					Node child = children.item(j);
					if (child.getNodeType() == Node.ELEMENT_NODE){
						if(child.getNodeName().equalsIgnoreCase("input"))//inputs
							getCompareInput(child.getAttributes().getNamedItem("path"), cInputs);
						else if(child.getNodeName().equalsIgnoreCase("parameter")){
							cParameters.put(child.getAttributes().item(0).getNodeValue(), child.getAttributes().item(1).getNodeValue());
						}
					}

				}
				comparesInputs.put(compareId, cInputs);
				comparesParameters.put(compareId, cParameters);
				boolean multipleCompares =false;
				if(comparesAttributes.size()>1)
					multipleCompares =  true;
				for (String comp : comparesAttributes.keySet()){
					String metric=comparesAttributes.get(comp).get("metric"); //get metric
					String threshold = "";
					String expression = "";
					if(comparesAttributes.get(comp).containsKey("threshold"))
						threshold = comparesAttributes.get(comp).get("threshold"); //get threshold
				}
				NodeList inputs = compare.getChildNodes();
				for(int j=0; j<inputs.getLength();j++){
					Node input = inputs.item(j);
					if (input.getNodeType() == Node.ELEMENT_NODE){
						String path= input.getAttributes().getNamedItem("path").getNodeValue(); // only path should be here in <Input>
						String[] property = path.substring(1).split("/");
						cInputs.put(property[0], property[1]);
					}
				}
				comparesInputs.put(compareId,cInputs);
				//parameters
			}
		}
		if(metricExpression==null)
			metricExpression="";
		if(list.getLength() > 1){ // multiple compares
			NodeList aggregateList = xmlDocument.getElementsByTagName("Aggregate");
			aggregationFn = aggregateList.item(0).getAttributes().item(0).getNodeValue(); // i.e. average
			metricExpression+=aggregationFn+"(";
			for (String expr : expressions.keySet()) {
				metricExpression+="("+expr+"|"+expressions.get(expr)+")";
			}
		}
		else // one compare
			for (String expr : expressions.keySet()) {
				metricExpression+=expr;
			}
	}
	
	
	/**
	 * @param input
	 * @param cInputs
	 * @author mofeed
	 */
	public void getCompareInput(Node input,HashMap<String,String> cInputs)
	{
		String path= input.getNodeValue(); // only path should be here in <Input>
		String[] property = path.substring(1).split("/");
		cInputs.put(property[0], property[1]);
	}
	public void processOutput() throws Exception{
		NodeList list = xmlDocument.getElementsByTagName("Output");
		if(list.getLength() == 0){
			logger.error("Missed Tag -- Output");
			list = xmlDocument.getElementsByTagName("Outputs");//check if the overall Outputs tag exists but no specified actual output (which is possible--don't want the results in file)
			if(list.getLength()== 0)//no Outputs too
				throw new Exception("problem in Output tag");
			else{
				acceptanceFile = "";
				outputFormat = "";
				acceptanceThreshold = 0;
				return;
			}
		}
		Map<String,String> outputAttributes = new HashMap<String, String>();
		Map<String,String> outputParameters = new HashMap<String, String>();
		for(int i=0; i < list.getLength();i++){
			Node output = list.item(i);
			if (output.getNodeType() == Node.ELEMENT_NODE){
				NamedNodeMap outputAttr = output.getAttributes();
				for(int j=0;j < outputAttr.getLength();j++){
					if(outputAttr.item(j).getNodeName().equalsIgnoreCase("minconfidence"))
						outputAttributes.put("minConfidence", outputAttr.item(j).getNodeValue());
					else if(outputAttr.item(j).getNodeName().equalsIgnoreCase("maxconfidence"))
						outputAttributes.put("maxConfidence", outputAttr.item(j).getNodeValue());
					else if(outputAttr.item(j).getNodeName().equalsIgnoreCase("type"))
						outputAttributes.put("type", outputAttr.item(j).getNodeValue());
				}
				NodeList outputParams= output.getChildNodes();
				for(int j=0;j < outputParams.getLength();j++){
					if (outputParams.item(j).getNodeType() == Node.ELEMENT_NODE){
						NamedNodeMap paramAttr = outputParams.item(j).getAttributes();
						outputParameters.put(paramAttr.getNamedItem("name").getNodeValue(), paramAttr.getNamedItem("value").getNodeValue());
					}
				}

			}
		}
		acceptanceFile = outputParameters.get("file");
		outputFormat = outputParameters.get("format");
		if(outputAttributes.get("minConfidence")!=null)
			acceptanceThreshold = Double.parseDouble(outputAttributes.get("minConfidence"));
		else if (outputAttributes.get("maxConfidence")!=null)
			acceptanceThreshold = Double.parseDouble(outputAttributes.get("maxConfidence"));
		else
			acceptanceThreshold = 0;
	}

	
	/**
	 * @throws Exception
	 * @author mofeed
	 */
	public void processLinkType() throws Exception
	{
		NodeList list = xmlDocument.getElementsByTagName("LinkType");
		if(list.getLength() == 0){
			logger.error("Missed Tag -- LinkType");
			throw new Exception("problem in LinkType tag");

		}
		acceptanceRelation = list.item(0).getChildNodes().item(0).getNodeValue();
		if(acceptanceRelation.endsWith(">"))
			acceptanceRelation = acceptanceRelation.substring(0, acceptanceRelation.length()-1);
		if(acceptanceRelation.startsWith("<"))
			acceptanceRelation = acceptanceRelation.substring(1);
		///default value as not matched to something to silk
		verificationRelation = acceptanceRelation;
	}
	/**
	 * @throws Exception
	 * @author mofeed
	 */
	public void processBlocking() throws Exception
	{
		NodeList list = xmlDocument.getElementsByTagName("Blocking");
		if(list.getLength() > 0 ){ // there is blocking tag
			int BlockingNr =list.getLength();
			for (int i=0;i< BlockingNr;i++){ // for each prefix
				Node Block = list.item(i); 
				if (Block.getNodeType() == Node.ELEMENT_NODE){ // for each prefix there is additional text element (empty one) which is bypassed
					NamedNodeMap attributes = Block.getAttributes();
					if(attributes.getLength() > 0)
						blocking =Integer.parseInt( Block.getAttributes().getNamedItem("blocks").getNodeValue());
				}
			}
		}
	}
	
	
	/**
	 * @param Tag
	 * @throws Exception
	 * @author mofeed
	 */
	public void processTag(String Tag) throws Exception{
		if(Tag.equalsIgnoreCase("prefixes"))
			processPrefixes();
		else if (Tag.equalsIgnoreCase("dataset")){
			processDataSets(sourceInfo, "SourceDataset");
			processDataSets(targetInfo, "TargetDataset");
		}
		else if (Tag.equalsIgnoreCase("datasource"))
			processDataSource();
		else if (Tag.equalsIgnoreCase("linkcondition"))
			//processLinkCondition();
			processLinkConditionRec();
		else if (Tag.equalsIgnoreCase("linktype"))
			processLinkType();
		else if (Tag.equalsIgnoreCase("output"))
			processOutput();	
		else if(Tag.equalsIgnoreCase("blocking"))
			processBlocking();
	}
	
	
	/**
	 * @param input
	 * @throws Exception
	 * @author mofeed
	 */
	public void initializeDOM4SILK(InputStream input) throws Exception{
		try{
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			xmlDocument = builder.parse(input);
			xmlDocument.getDocumentElement().normalize();
			NodeList list = xmlDocument.getElementsByTagName("Silk");
			if(list.getLength() > 1){
				logger.error("Wrong Spec format --more tha on SILK tag");
				System.exit(1);
			}
		}
		catch (Exception e) {
			throw new Exception("initializeDOM4SILK: "+ e.getMessage());
		}
	}
	
	
	/**
	 * @param node
	 * @author mofeed
	 */
	public static void clean(Node node){
		for(int n = 0; n < node.getChildNodes().getLength(); n++){
			Node child = node.getChildNodes().item(n);
			if(child.getNodeType() == 8 || (child.getNodeType() == 3 && testWhiteSpace(child.getNodeValue()))){
				node.removeChild(child);
				n--;
			}
			else if(child.getNodeType() == 1){
				clean(child);
			}
		}
	}

	
	/**
	 * @param 
	 * @return
	 * @author mofeed
	 */
	public static boolean testWhiteSpace(String s){
		if(s.equals("\n") || s.equals("\r") || s.equals("\t"))
			return true;
		return false;
	}
	/**
	 * @param s
	 * @return
	 * @author mofeed
	 */
	public void enrichModelWithMetaData(Model m, Resource s, String enrichingFile)
	{
		List<String> fileLines = ReadWRiteFile.readFile(enrichingFile);
		for (String line : fileLines) {
			String[] parts =line.split(" ");
			String objectString ="";
			for(int i=1;i<parts.length;i++)
				objectString+=parts[i]+" ";
			objectString = objectString.trim();
			String propertyString =  parts[0].trim();
			if(parts[1].startsWith("http") &&parts[1].contains(":") )
			{
				Resource obj = ResourceFactory.createResource((objectString));
				m.add(s,ResourceFactory.createProperty(propertyString),obj);
			}
			else
				m.add(s,ResourceFactory.createProperty(propertyString), objectString);
		}
	}
	
	
	/**
	 * @param m
	 * @param p
	 * @return
	 * @author sherif
	 */
	private Resource createResource(Model m, String p) {
		if(p.contains(":") && !(p.startsWith("http://"))){
			String pPrefix = p.substring(0, p.indexOf(":"));
			if(!m.getNsPrefixMap().containsKey(pPrefix)){
				logger.error("Undefined prefix " + pPrefix);
				System.exit(1);
			}
			String pPrefixUri = m.getNsPrefixMap().get(pPrefix);
			p = p.replace(pPrefix+":", pPrefixUri);
		}
		return ResourceFactory.createResource(p);
	}
	
	
	/**
	 * @param filePath
	 * @return
	 * @throws Exception
	 * @author sherif
	 */
	public Model xmlConfigToRDFConfigExtended(String filePath) throws Exception{
		validateAndRead(filePath);
		Model m = ModelFactory.createDefaultModel();
		String uri = LIMES.uri + filePath.substring(filePath.lastIndexOf("/"), filePath.lastIndexOf("."));
		Resource s = ResourceFactory.createResource(uri); 
		m.add(s, RDF.type, LIMES.LimesSpecs);

		// Prefixes
		m.setNsPrefixes(prefixes);
		m.setNsPrefix(LIMES.prefix, LIMES.uri);
		m.setNsPrefix("owl", OWL.NS);
		m.setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
		m.setNsPrefix("swpo", "http://sw-portal.deri.org/ontologies/swportal#");
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
		
		enrichModelWithMetaData(m, s, enrichFile);
		return m;
	}
	
	
	/**
	 * @param specsPaths
	 * @return
	 * @author mofeed
	 */
	public  static List<String> getSpecsFiles(String specsPaths){
		List<String> specFiles =  new ArrayList<String>();
		File folder = new File(specsPaths);
		File[] listOfFiles = folder.listFiles();

		for (int i = 0; i < listOfFiles.length; i++){
			if (listOfFiles[i].isFile()){
				if(listOfFiles[i].getName().endsWith(".xml"))
					specFiles.add(listOfFiles[i].getAbsolutePath());
			}
			else if (listOfFiles[i].isDirectory()) 
				specFiles.addAll(getSpecsFiles(specsPaths+"/"+listOfFiles[i].getName()));
		}
		return specFiles;
	}
	
	
	/*********************************  MAIN Method  ************************************/
	/**
	 * @param args
	 * @author mofeed
	 */
	public static void main(String args[]) {
		String specsSourceFolder = args[0].trim();
		File f = new File(specsSourceFolder +"/meta-data");
		if(f.exists() && !f.isDirectory()) 
		{ enrichFile = f.getAbsolutePath(); }
					
		problematicFiles= new HashMap<String, String>();
		List<String> specFiles =null;
		try{
			specFiles = getSpecsFiles(specsSourceFolder);
		} catch (Exception e){
			logger.error("problem in loading files\n"+e.getMessage());
			System.exit(1);
		}
		int totalFiles = specFiles.size();
		int totalNonConverted =0;
		System.out.println("Number of files = "+ totalFiles);
		SILKConfigReader cr = new SILKConfigReader(); //ConfigReaderSILK
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
		for (String fileName : problematicFiles.keySet()) {
			totalNonConverted++;
			System.out.println("Error in file "+ fileName + " : "+ problematicFiles.get(fileName));
		}
		System.out.println("Total number of successfully converted file = "+(totalFiles-totalNonConverted) +" out of "+ totalFiles);
	}
}
