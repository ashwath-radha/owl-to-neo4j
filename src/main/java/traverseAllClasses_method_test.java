import org.neo4j.graphdb.*;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.index.UniqueFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.*;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;
import org.semanticweb.owlapi.search.EntitySearcher;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class traverseAllClasses_method_test
{
    /* Set up OWL input file, create ontology manager, an empty ontology and the data factory.
    Then set up the file path to store the neo4j graph and create a GraphDatabaseFactory */
    private static File ont = new File("/Users/ARAD/Desktop/College/summer_2018/andersen_lab_stsi/HumanDiseaseOntology/src/ontology/doid-merged.owl");
    private static OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    private static OWLOntology ontology;
    private static OWLDataFactory ontFactory = manager.getOWLDataFactory();

    static
    {
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


    /* Each class has an individual IRI unrelated to the one present at the
    top of the document */
    // only works for classes, not object annotation props
    public static HashMap<String, String> getRDFSyeLabel(IRI i)
    {
        String iri = i.toString(), prop, label;
        OWLClass c;

        // removed local variables for ontFactory and ontology
        HashMap<String, String> m = new HashMap<>();

        c = ontFactory.getOWLClass(iri);

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

    public void traverseAllClasses()
    {
        reasoner.precomputeInferences();
        int count = 0;
        for (Object o : ontology.classesInSignature().toArray())
        {
            if (count == 10000) {
                break;
            }

            OWLClass c = (OWLClass) o;
            System.out.println("Class: "+getRDFSyeLabel(c.getIRI()) + " IRI: "+c.getIRI());

            for (Object obj : reasoner.getSuperClasses(c, true).entities().toArray())
            {
                OWLClass cls = (OWLClass) obj;
                System.out.println(cls.getIRI());
            }
            for( OWLAxiom axiom : ontology.axioms( c ).collect( Collectors.toSet() ) )
            {
                System.out.println( "\tAxiom: " + axiom.toString() );
            }
            System.out.println();
            count++;
        }
    }

    public static void main(String[] args)
    {
        traverseAllClasses_method_test m = new traverseAllClasses_method_test();
        m.traverseAllClasses();

        System.out.println("Success");
    }
}