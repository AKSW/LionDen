/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package helper;

import io.ConfigReader;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Contains the infos necessary to access a knowledge base
 *
 * @author ngonga
 */
public class KBInfo implements Serializable{

    public String id;
    public String endpoint;
    public String graph;
    public String var;
    // properties contain the list of properties whose values will be used during
    // the mapping process
    public List<String> properties;
    // restrictions specify the type of instances to be taken into consideration
    // while mapping
    public ArrayList<String> restrictions;
    // maps each property to a new label and the corresponding preprocessing chain
    // the map might contain property -> <property, "">, meaning nothing is to be preprocessed and
    // the property keeps its name. property -> <x, y> means the property gets the label x and
    // is renamed y after the preprocessing
    public Map<String, Map<String, String>> functions;
    public Map<String, String> prefixes;
    public int pageSize;
    public String type; //can be sparql or csv, TODO add N3

    /**
     * Constructor
     */
    public KBInfo() {
        id = null;
        endpoint = null;
        graph = null;
        restrictions = new ArrayList<String>();
        properties = new ArrayList<String>();
        prefixes = new HashMap<String, String>();
        functions = new HashMap<String, Map<String, String>>();
        //-1 means query all at once
        pageSize = -1;
        type = "sparql"; //default value
    }
    
    /**
     * @param var
     *@author sherif
     */
    public KBInfo(String var) {
        this();
        this.var = var;
    }

    /**
	 * @param id
	 * @param endpoint
	 * @param graph
	 * @param var
	 * @param properties
	 * @param restrictions
	 * @param functions
	 * @param prefixes
	 * @param pageSize
	 * @param type
	 *@author sherif
	 */
	public KBInfo(String id, String endpoint, String graph, String var,
			List<String> properties, ArrayList<String> restrictions,
			Map<String, Map<String, String>> functions,
			Map<String, String> prefixes, int pageSize, String type) {
		super();
		this.id = id;
		this.endpoint = endpoint;
		this.graph = graph;
		this.var = var;
		this.properties = properties;
		this.restrictions = restrictions;
		this.functions = functions;
		this.prefixes = prefixes;
		this.pageSize = pageSize;
		this.type = type;
	}

	/**
     *
     * @return String representation of knowledge base info
     */
    @Override
    public String toString() {
        String s = "ID: " + id + "\n";
        s = s + "Var: " + var + "\n";
        s = s + "Prefixes: " + prefixes + "\n";
        s = s + "Endpoint: " + endpoint + "\n";
        s = s + "Graph: " + graph + "\n";
        s = s + "Restrictions: " + restrictions + "\n";
        s = s + "Properties: " + properties + "\n";
        s = s + "Functions: " + functions + "\n";
        s = s + "Page size: " + pageSize + "\n";
        s = s + "Type: " + type + "\n";
        return s;
    }

    /**
     * Compute a hash code for the knowledge base encoded by this KBInfo. Allow
     * the hybrid cache to cache and retrieve the content of remote knowledge
     * bases on the hard drive for the user's convenience
     *
     * @return The hash code of this KBInfo
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((endpoint == null) ? 0 : endpoint.hashCode());
        result = prime * result + ((graph == null) ? 0 : graph.hashCode());
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + pageSize;
        result = prime * result
                + ((prefixes == null) ? 0 : prefixes.hashCode());
        result = prime * result
                + ((properties == null) ? 0 : properties.hashCode());
        result = prime * result
                + ((restrictions == null) ? 0 : restrictions.hashCode());
        //result = prime * result + ((var == null) ? 0 : var.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        KBInfo other = (KBInfo) obj;
        if (endpoint == null) {
            if (other.endpoint != null) {
                return false;
            }
        } else if (!endpoint.equals(other.endpoint)) {
            return false;
        }
        if (graph == null) {
            if (other.graph != null) {
                return false;
            }
        } else if (!graph.equals(other.graph)) {
            return false;
        }
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!id.equals(other.id)) {
            return false;
        }
        if (pageSize != other.pageSize) {
            return false;
        }
        if (prefixes == null) {
            if (other.prefixes != null) {
                return false;
            }
        } else if (!prefixes.equals(other.prefixes)) {
            return false;
        }
        if (properties == null) {
            if (other.properties != null) {
                return false;
            }
        } else if (!properties.equals(other.properties)) {
            return false;
        }
        if (restrictions == null) {
            if (other.restrictions != null) {
                return false;
            }
        } else if (!restrictions.equals(other.restrictions)) {
            return false;
        }
        if (var == null) {
            if (other.var != null) {
                return false;
            }
        } else if (!var.equals(other.var)) {
            return false;
        }
        return true;
    }

    /** Returns the class contained in the restriction
     *
     * @return Class label
     */
    public String getClassOfendpoint() {
        for (String rest : restrictions) {
            if (rest.matches(".* rdf:type .*")) {
                String result = rest.substring(rest.indexOf("rdf:type") + 8).replaceAll("<", "").replaceAll(">", "").trim();
                return result;
            }
        }
        return null;
    }

      /** Returns the class contained in the restriction
     *
     * @return Class label
     */
    public String getClassOfendpoint(boolean expanded) {
        for (String rest : restrictions) {
            if (rest.matches(".* rdf:type .*")) {
                String result = rest.substring(rest.indexOf("rdf:type") + 8).replaceAll("<", "").replaceAll(">", "").trim();
                if(!expanded) return result;
                else
                {
                    String namespace = result.substring(0, result.indexOf(":"));
                    if(prefixes.containsKey(namespace))
                        return prefixes.get(namespace)+result.substring(result.indexOf(":")+1);
                    else return result;
                }
            }
        }
        return null;
    }
    /**
     * Returns class URI if restriction to a rdf:type exists
     *
     * @return
     */
    public String getClassRestriction() {
        String ret = null;
        for (String s : restrictions) {
            if (s.indexOf("rdf:type") > -1) {
                ret = s.substring(s.indexOf("rdf:type") + 8).trim();
            }
        }

        return ret;
    }

    public String getPrefix(String baseUri) {
        if (prefixes.containsValue(baseUri)) {
            for (Entry<String, String> e : prefixes.entrySet()) {
                if (e.getValue().equals(baseUri)) {
                    return e.getKey();
                }
            }
        }
        return null;
    }

    public void afterPropertiesSet() {
        List<String> copy = new ArrayList<String>(properties);
        properties.clear();

        for(String property : copy) {
            ConfigReader.processProperty(this, property);
        }
    }
}
