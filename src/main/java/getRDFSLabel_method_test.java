import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.*;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;
import org.semanticweb.owlapi.search.EntitySearcher;

import java.io.File;
import java.util.HashMap;

public class getRDFSLabel_method_test
{
    /* Set up OWL input file, create ontology manager, an empty ontology and
    the data factory. Then set up the file path to store the neo4j graph and create
    a GraphDatabaseFactory */
    private static File ont = new File("/Users/ARAD/Desktop/College/summer_2018/andersen_lab_stsi/HumanDiseaseOntology/src/ontology/doid-merged.owl");
    private static OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    private static OWLOntology ontology;
    private static OWLDataFactory ontFactory = manager.getOWLDataFactory();

    /* path for storing neo4j database */
    //File graphPath = new File("/Users/ARAD/Desktop/neo4j/pds");
    //private GraphDatabaseService graphdb = new GraphDatabaseFactory().newEmbeddedDatabase(graphPath);

    /* Check for exceptions when loading ontology */
    static {
        try {
            ontology = manager.loadOntologyFromOntologyDocument(ont);
        } catch (OWLOntologyCreationException e) {
            e.printStackTrace();
        }
    }

    /* Create an OWLReasoner */
    private static OWLReasonerFactory reasonerFactory = new StructuralReasonerFactory();
    private static ConsoleProgressMonitor progressMonitor = new ConsoleProgressMonitor();
    private static OWLReasonerConfiguration config = new SimpleConfiguration(progressMonitor);
    private static OWLReasoner reasoner = reasonerFactory.createReasoner(ontology, config);


    /* explain IRI concept, does each class have an IRI, is it the same as the
    ID that we see, or is it related to the general IRI at the top of the xml file
     */
    //only works for classes, not object annotation props
    public static HashMap<String, String> getRDFSLabel(IRI i)
    {
        String iri = i.toString(), prop, label;
        OWLClass c;

        // removed local variables for ontFactory and ontology
        HashMap<String, String> m = new HashMap<>();

        c = ontFactory.getOWLClass(iri);
        /* there is nothing in the documentation regarding a getRDFSLabel method
        with no input, so what does it return
         */
        for (Object j : EntitySearcher.getAnnotations(c, ontology, ontFactory.getRDFSLabel()).toArray())
        {
            OWLAnnotation a = (OWLAnnotation) j;
            //System.out.println(a.getValue());
            prop = a.getProperty().toString();
            OWLAnnotationValue val = a.getValue();
            label = ((OWLLiteral) val).getLiteral();
            m.put(prop, label);
        }
        return m;
    }

    // demonstrates that the key is 'rdfs:label'
    // and that the value is the "name" such as the disease name etc...
    public static void main(String[] args)
    {
        for(Object o: ontology.classesInSignature().toArray())
        {
            OWLClass c = (OWLClass) o;
            HashMap<String, String> x = getRDFSLabel(c.getIRI());
            for(String name: x.keySet())
            {
                String key = name;
                String value = x.get(name);
                System.out.println(key + " " + value);
            }
        }
        System.out.println("Success");
    }
}
