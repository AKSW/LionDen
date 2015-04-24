/**
 * 
 */
package generator;

import helper.LDEN;
import io.LimesConfigReader;
import io.RDFSpecs;
import io.SilkConfigReader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;



/**
 * @author sherif
 *
 */
public class SpecsKBGenerator {
	private static final Logger logger = Logger.getLogger(SpecsKBGenerator.class.getName());
	private final static String LIME = "limes";
	private final static String SILK = "silk";
	private static final String ADD_DATA = "add.txt";
	
	public static void main(String args[]) {
		if(args.length < 3){
			logger.info("please add 2 parameters:\t"
					+ "(1) Linking framework [LIMES/SILK]\t"
					+ "(2) Input folder"
					+ "(3) Output folder");
			System.exit(1);
		}
		String frameWork = new String();
		if(args[0].equalsIgnoreCase(LIME)){
			frameWork = LIME;
		}else if(args[0].equalsIgnoreCase(SILK)){
			frameWork = SILK;
		}else{
			logger.error("No implementation for: " + args[0] );
		}
		String inputFolder = args[1];
		String outputFolder = args[2];
		generateDataset(frameWork, inputFolder, outputFolder);
	}

	/**
	 * @param frameWork
	 * @param inputFolder
	 * @param outputFolder
	 * @author sherif
	 */
	private static void generateDataset(String frameWork, String inputFolder, String outputFolder) {
		RDFSpecs cr = null;
		if(frameWork.equalsIgnoreCase(LIME)){
			cr = new LimesConfigReader();
		}else if(frameWork.equalsIgnoreCase(SILK)){
			cr = new SilkConfigReader();
		}
		
		List<String> specFiles = null;
		try{
			specFiles = getSpecsFiles(inputFolder);
		} catch (Exception e){
			logger.error("Problem in loading files\n" + e.getMessage());
			System.exit(1);
		}
		System.out.println("Number of files = "+ specFiles.size());
		long starTime = System.currentTimeMillis();
		FileWriter fileWriter;
		for (String specFile : specFiles) {
			try {
				logger.info("Read file: " + specFile);
				String outputFile = outputFolder + "/" + specFile.substring(specFile.lastIndexOf("/")+1, specFile.lastIndexOf(".")) + ".ttl";
				fileWriter = new FileWriter(outputFile);
				Model m = ModelFactory.createDefaultModel();
				try {
					m = cr.xmlConfigToRDFConfigExtended(specFile);
				} catch (Exception e) {
					e.printStackTrace();
				}
				Model additionalData = GenerateAdditionalData(inputFolder, specFile);
				m.add(additionalData);
				m.write(fileWriter, "TTL");
				logger.info("Done in " + (System.currentTimeMillis() - starTime) + "ms");
				logger.info("Converted file saved to " + outputFile);
			} catch (Exception e) { 
				System.out.println(e.getMessage());
			}
		}
	}
	
	/**
	 * @param inputFolder
	 * @param s
	 * @return Model that contains the additional data included in the add.txt file in the input folder 
	 * @throws Exception
	 * @author sherif
	 */
	public static Model GenerateAdditionalData(String inputFolder, String specsFile ) throws Exception{
		Model result = ModelFactory.createDefaultModel();
		Resource  s = ResourceFactory.createResource(specsFile);
		String uri = LDEN.uri + specsFile.substring(specsFile.lastIndexOf("/"), specsFile.lastIndexOf("."));
        BufferedReader bReader = new BufferedReader(new FileReader(inputFolder + ADD_DATA));
        String line;
        while ((line = bReader.readLine()) != null) {
            String datavalue[] = line.split("\t");
            Property p = ResourceFactory.createProperty(datavalue[0]);
            String object = datavalue[1];
            if(object.contains("\"")){
            	object.replaceAll("\"", "");
            	Literal o = ResourceFactory.createPlainLiteral(object);
            	result.add(s, p, o);
            }else {
            	Resource o = ResourceFactory.createResource(object);
            	result.add(s, p, o);
            }
        }
        bReader.close();
		return result;
	}
	
	/**
	 * @param specsPaths
	 * @return list of all xml files included in the input specsPaths directory
	 * @author sherif
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
}
