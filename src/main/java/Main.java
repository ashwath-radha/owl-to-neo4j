import org.neo4j.graphdb.*;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.index.UniqueFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.*;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.util.OWLOntologyWalker;
import org.semanticweb.owlapi.util.OWLOntologyWalkerVisitorEx;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class Main {

    //Load ontology document from local machine, create manager and variable ontology
    //Create ontFactory (OWLDataFactory) for creating entities and axioms

    //private static File ont = new File("/Users/karthik/Documents/HumanDiseaseOntology/src/ontology/doid-merged.owl");
    private static File ont = new File("/Users/ARAD/Desktop/College/summer_2018/andersen_lab_stsi/HumanDiseaseOntology/src/ontology/doid-merged.owl");
    private static OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    private static OWLOntology ontology;
    private static OWLDataFactory ontFactory = manager.getOWLDataFactory();

/*
    private static File taxonOnt = new File("/Users/karthik/Documents/HumanDiseaseOntology/src/ontology/imports/ncbitaxon_import.owl");
    private static OWLOntologyManager taxonManager = OWLManager.createOWLOntologyManager();
    private static OWLOntology taxonOntology;
    private static OWLDataFactory taxonFactory = taxonManager.getOWLDataFactory();

    private static File hpOnt = new File("/Users/karthik/Documents/HumanDiseaseOntology/src/ontology/imports/hp_import.owl");
    private static OWLOntologyManager hpManager = OWLManager.createOWLOntologyManager();
    private static OWLOntology hpOntology;
    private static OWLDataFactory hpFactory = hpManager.getOWLDataFactory();

    private static File uberonOnt = new File("/Users/karthik/Documents/HumanDiseaseOntology/src/ontology/imports/uberon_import.owl");
    private static OWLOntologyManager uberonManager = OWLManager.createOWLOntologyManager();
    private static OWLOntology uberonOntology;
    private static OWLDataFactory uberonFactory = uberonManager.getOWLDataFactory();

    private static File clOnt = new File("/Users/karthik/Documents/HumanDiseaseOntology/src/ontology/imports/cl_import.owl");
    private static OWLOntologyManager clManager = OWLManager.createOWLOntologyManager();
    private static OWLOntology clOntology;
    private static OWLDataFactory clFactory = clManager.getOWLDataFactory();

    private static File relOnt = new File("/Users/karthik/Documents/HumanDiseaseOntology/src/ontology/imports/relations.owl");
    private static OWLOntologyManager relManager = OWLManager.createOWLOntologyManager();
    private static OWLOntology relOntology;
    private static OWLDataFactory relFactory = relManager.getOWLDataFactory();

    private static File soOnt = new File("/Users/karthik/Documents/HumanDiseaseOntology/src/ontology/imports/so.owl");
    private static OWLOntologyManager soManager = OWLManager.createOWLOntologyManager();
    private static OWLOntology soOntology;
    private static OWLDataFactory soFactory = soManager.getOWLDataFactory();

    private static File omimOnt = new File("/Users/karthik/Documents/HumanDiseaseOntology/src/ontology/imports/omim_susc_import.owl");
    private static OWLOntologyManager omimManager = OWLManager.createOWLOntologyManager();
    private static OWLOntology omimOntology;
    private static OWLDataFactory omimFactory = omimManager.getOWLDataFactory();
*/
    //labeling a graphPath using this Java's File
    //graphdb is an instance of GraphDatabaseService, which allows to create nodes and work with Neo4j
    //instantiated graphdb
    //File graphPath = new File("/Users/karthik/neo4j/pds");
    File graphPath = new File("/Users/ARAD/Desktop/neo4j/pds2");
    private GraphDatabaseService graphdb = new GraphDatabaseFactory().newEmbeddedDatabase(graphPath);

    //deals with exceptions when loading ont file into ontology
    //now it is an OWLOntology
    static {
        try {
            ontology = manager.loadOntologyFromOntologyDocument(ont);
            /*taxonOntology = taxonManager.loadOntologyFromOntologyDocument(taxonOnt);
            hpOntology = hpManager.loadOntologyFromOntologyDocument(hpOnt);
            uberonOntology = uberonManager.loadOntologyFromOntologyDocument(uberonOnt);
            clOntology = clManager.loadOntologyFromOntologyDocument(clOnt);
            relOntology = relManager.loadOntologyFromOntologyDocument(relOnt);
            soOntology = soManager.loadOntologyFromOntologyDocument(soOnt);
            omimOntology = omimManager.loadOntologyFromOntologyDocument(omimOnt);*/
        } catch (OWLOntologyCreationException e) {
            e.printStackTrace();
        }
    }

    //reasonerFactory to reason over ontology
    //specify progress monitory via a configuration
    //create reasoner using reasonerFactory's method
    //reasoner allows us to access subclasses, superclasses
    private static OWLReasonerFactory reasonerFactory = new StructuralReasonerFactory();
    private static ConsoleProgressMonitor progressMonitor = new ConsoleProgressMonitor();
    private static OWLReasonerConfiguration config = new SimpleConfiguration(progressMonitor);
    private static OWLReasoner reasoner = reasonerFactory.createReasoner(ontology, config);

    //converts IRI to string, redefines ontFactory and ontology as f and ont
    //creates empty HashMap m, prop and label will be added
    //c stores instance of OWLClass with passed in IRI i
    //EntitySearcher gets Annotations that fulfill property of f.getRDFSLabel()
    //these annotations converted to an array, and then for loop traverses them
    //for each, the property that this annotation acts along and its value is retrieved
    //the property (prop) is the key and the label is the value
    public static HashMap<String, String> getRDFSLabel(IRI i) {
        String iri = i.toString(), prop, label;
        OWLClass c;
        OWLDataFactory f = ontFactory;
        OWLOntology ont = ontology;
        HashMap<String, String> m = new HashMap<>();
 /*       if(iri.contains("NCBITaxon_")){
            f = taxonFactory;
            ont = taxonOntology;
        } else if(iri.contains("HP_")){
            f = hpFactory;
            ont=hpOntology;
        } else if(iri.contains("UBERON_")){
            f = uberonFactory;
            ont = uberonOntology;
        } else if(iri.contains("OMIM_")){
            f = omimFactory;
            ont = omimOntology;
        } else if(iri.contains("SO_")){
            f = soFactory;
            ont = soOntology;
        } else if(iri.contains("RO_")){
            f = relFactory;
            ont = relOntology;
        } else if(iri.contains("CL_")){
            f = clFactory;
            ont = clOntology;
        }*/
        c = f.getOWLClass(iri);
        for (Object j : EntitySearcher.getAnnotations(c, ont, f.getRDFSLabel()).toArray()) {
            OWLAnnotation a = (OWLAnnotation) j;
            //System.out.println(a.getValue());
//            if (a.getValue() instanceof OWLLiteral) {
            prop = a.getProperty().toString();
            OWLAnnotationValue val = a.getValue();
            label = ((OWLLiteral) val).getLiteral();
            m.put(prop, label);
//            }
        }
        return m;
    }

    // why are we printing this...
    /* These restrictions are merely to show how interconnected certain classes
    are. Will be used later... */
    public static void printQuantifiedRestriction( OWLClass oc, OWLQuantifiedObjectRestriction restriction ) {
        /*System.out.println( "\t\tClass: " + oc.toString() );
        System.out.println( "\t\tClassExpressionType: " + restriction.getClassExpressionType().toString() );
        System.out.println( "\t\tProperty: "+ restriction.getProperty().toString() );
        System.out.println( "\t\tObject: " + restriction.getFiller().toString() );*/
        getRDFSLabel(restriction.getProperty().asOWLObjectProperty().getIRI());
        getRDFSLabel(restriction.getFiller().asOWLClass().getIRI());
        System.out.println();
    }

    /* cardinality restriction is used to make a property required, allow a specific
    number of values for the property or to insist a property not to occur */
    public static void printCardinalityRestriction( OWLClass oc, OWLObjectCardinalityRestriction restriction ) {
        System.out.println( "\t\tClass: " + oc.toString() );
        System.out.println( "\t\tClassExpressionType: " + restriction.getClassExpressionType().toString() );
        System.out.println( "\t\tCardinality: " + restriction.getCardinality() );
        System.out.println( "\t\tProperty: "+ restriction.getFiller().toString() );
        System.out.println( "\t\tObject: " + restriction.getFiller().toString() );
        System.out.println();
    }

    //beginTx creates a new transaction
    //creates relationship between two input nodes
    //if hashmap is not empty, each entry is broken down and set as a property

    public void addRelationShip(org.neo4j.graphdb.Node n, org.neo4j.graphdb.Node p, String type, HashMap<String, String> m){
        try ( Transaction tx = graphdb.beginTx() ) {
            Relationship r = n.createRelationshipTo(p, RelationshipType.withName(type));
            if(m!=null){
                for (Map.Entry<String, String> entry : m.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    r.setProperty(key, value);
                }
            }
            r.setProperty("LABEL", type.split("_")[0]);
            tx.success();
        }
    }
    //executes a query and returns iterable stored in result
    //iterate through while loop
    //adding column keys and values to row, print rows
    public void executeQuery(String query){
        try ( Transaction tx = graphdb.beginTx() ) {
            String rows ="";
            Result result = graphdb.execute( query);
            while ( result.hasNext() )
            {
                Map<String,Object> row = result.next();
                for (Map.Entry<String,Object> column : row.entrySet() )
                {
                    rows += column.getKey() + ": " + column.getValue() + "; ";
                }
                rows += "\n";
            }
            System.out.print(rows);
            tx.success();
        }
    }

    //returns a neo4j Node
    //where are we applying the initialize method...is it initializing the node we plan to return?

    public org.neo4j.graphdb.Node getOrCreateUserWithUniqueFactory(String id, HashMap<String, String> m) {
        org.neo4j.graphdb.Node n;
        try ( Transaction tx = graphdb.beginTx() )
        {
            // Database operations go here
            UniqueFactory<org.neo4j.graphdb.Node> gfactory = new UniqueFactory.UniqueNodeFactory(graphdb, "Nodes") {
                @Override
                protected void initialize(org.neo4j.graphdb.Node created, Map<String, Object> properties) {
                    for (Map.Entry<String, String> entry : m.entrySet()) {
                        String key = entry.getKey();
                        String value = entry.getValue();
                        created.setProperty(key, value);
                    }
                    //why is it using properties, how do we pass something into properties
                    created.setProperty( "id", properties.get( "id" ) );
                    created.setProperty("LABEL", id.split("_")[0]);
                }
            };
            n = gfactory.getOrCreate( "id", id );
            tx.success();
        }

        return n;
    }

    // creating unnecessary global variables
    org.neo4j.graphdb.Node relNode;
    String relType;
    IRI relIri;

    //what is import closure?
    //how about sub class relationships?...covered with the getSuperClasses
    /* some are subclasses of an actual subClass while others are subclasses of a restriction,
    does the getSuperClasses loop account for that, or are they all isA relationships

     Does the reasoner.getsuperclasses only get the direct ones and not the restrictions??
     */


    public void traverseAllClasses(){

        reasoner.precomputeInferences();
        Integer ctr = 0;
        org.neo4j.graphdb.Node current, n;
        for(Object o: ontology.classesInSignature().toArray()){
            try ( Transaction tx = graphdb.beginTx() )
            {
                // Database operations go here
                tx.success();
            }
            OWLClass c = (OWLClass) o;
            //System.out.println(c.toString());
            current = getOrCreateUserWithUniqueFactory(c.getIRI().getFragment(), getRDFSLabel(c.getIRI()));
            /*NodeSet<OWLClass> subCls= reasoner.getSubClasses(c, true);
            for (OWLClass cls : subCls.getFlattened()) {
               //getRDFSLabel(cls.getIRI());
            }*/
            for (OWLClass cls : reasoner.getSuperClasses(c, true).getFlattened()) {
                //getRDFSLabel(cls.getIRI());
                n = getOrCreateUserWithUniqueFactory(cls.getIRI().getFragment(), getRDFSLabel(cls.getIRI()));
                addRelationShip(current, n ,"isA", null);
            }
            /* for (Object j: ontology.objectPropertiesInSignature().toArray()){
                OWLObjectPropertyExpression objectProperty = (OWLObjectPropertyExpression) j;

            }*/
    /* is the axiom able to provide the restriction...the eclipse xml editor displayed it as the class and axiom
    being separate, so why are we looping through the axioms of a class to get to the subClass restrictions
    when that should be done in the previous looping of the super classes
     */
            for( OWLAxiom axiom : ontology.axioms( c ).collect( Collectors.toSet() ) ) {
                relNode = null;
                relType = null;
                //System.out.println( "\tAxiom: " + axiom.toString() );
                final IRI b;
                // create an object visitor to get to the subClass restrictions
                axiom.accept( new OWLObjectVisitor() {

                    // found the subClassOf axiom
                    public void visit( OWLSubClassOfAxiom subClassAxiom ) {

                        // create an object visitor to read the underlying (subClassOf) restrictions
                        subClassAxiom.getSuperClass().accept( new OWLObjectVisitor() {

                            public void visit(OWLClass c){
                                //relNode = getOrCreateUserWithUniqueFactory(c.getIRI().getFragment(), getRDFSLabel(c.getIRI()));
                                //relType = c.getIRI().getFragment();
                            }

                            public void visit( OWLObjectSomeValuesFrom someValuesFromAxiom ) {
                                //printQuantifiedRestriction( c, someValuesFromAxiom );
                                relNode = getOrCreateUserWithUniqueFactory(someValuesFromAxiom.getFiller().asOWLClass().getIRI().getFragment(), getRDFSLabel(someValuesFromAxiom.getFiller().asOWLClass().getIRI()));
                                relIri = someValuesFromAxiom.getProperty().asOWLObjectProperty().getIRI();
                                relType = relIri.getFragment();

                            }
//where these labels coming from...i see someValuesFrom in the xml breakdown but not these...
                            public void visit( OWLObjectExactCardinality exactCardinalityAxiom ) {
                                printCardinalityRestriction( c, exactCardinalityAxiom );
                            }

                            public void visit( OWLObjectMinCardinality minCardinalityAxiom ) {
                                printCardinalityRestriction( c, minCardinalityAxiom );
                            }

                            public void visit( OWLObjectMaxCardinality maxCardinalityAxiom ) {
                                printCardinalityRestriction( c, maxCardinalityAxiom );
                            }

                            // TODO: same for AllValuesFrom etc.
                        });
                    }
//why equivalentclassesaxiom...nvm present in OWL file
                    public void visit( OWLEquivalentClassesAxiom equivalentClassAxiom ) {

                        // create an object visitor to read the underlying (subClassOf) restrictions
                        equivalentClassAxiom.accept( new OWLObjectVisitor() {

                            public void visit( OWLObjectSomeValuesFrom someValuesFromAxiom ) {
                                printQuantifiedRestriction( c, someValuesFromAxiom );
                            }

                            public void visit( OWLObjectExactCardinality exactCardinalityAxiom ) {
                                printCardinalityRestriction( c, exactCardinalityAxiom );
                            }

                            public void visit( OWLObjectMinCardinality minCardinalityAxiom ) {
                                printCardinalityRestriction( c, minCardinalityAxiom );
                            }

                            public void visit( OWLObjectMaxCardinality maxCardinalityAxiom ) {
                                printCardinalityRestriction( c, maxCardinalityAxiom );
                            }

                            // TODO: same for AllValuesFrom etc.
                        });
                    }
                });
                if(relNode != null)
                {
                    addRelationShip(current, relNode, relType, getRDFSLabel(relIri));
                }
            }
            ctr += 1;
        }
    }

    public void shutdownDatabase()
    {
        graphdb.shutdown();
    }

// is this the same thing as traverseAllClasses...never used elsewhere
    public void walkOntology(){
        OWLOntologyWalker walker = new OWLOntologyWalker(Collections.singleton(ontology));
        OWLOntologyWalkerVisitorEx<Object> visitor = new OWLOntologyWalkerVisitorEx<Object>(walker) {

            @Override
            public Object visit(OWLObjectSomeValuesFrom ce) {

                for(Object o: ce.classesInSignature().toArray()){
                    //System.out.println("Axiom: " + ce.toString());
                    OWLClass c = (OWLClass) o;
                    //System.out.println(ce.getProperty());
                    if(ce.getProperty().toString().compareTo("<http://purl.obolibrary.org/obo/IDO_0000664>") == 0){
                        OWLClass t = ontFactory.getOWLClass(c.getIRI());
                        System.out.println(t.toString());
                        for(Object j: EntitySearcher.getAnnotations(t, ontology, ontFactory.getRDFSLabel()).toArray()) {
                            OWLAnnotation a = (OWLAnnotation) j;
                            //System.out.println(a.toString());
                            if (a.getValue() instanceof OWLLiteral) {
                                OWLAnnotationValue val = a.getValue();
                                String label = ((OWLLiteral) val).getLiteral();
                                System.out.println(label);
                            }
                        }
                    }


                }
                //System.out.println(getCurrentAxiom().toString());
                return "";
            }
        };
        walker.walkStructure(visitor);
    }

    public static OWLOntology getOntology()
    {
        return ontology;
    }

    public static void main(String [] args) throws OWLOntologyCreationException {
        long startTime = System.nanoTime();
        Main m = new Main();
        //m.traverseAllClasses();
        //long endTime   = System.nanoTime();
        //long totalTime = endTime - startTime;
        //System.out.println("Time to build database: "+ totalTime);
        //m.executeQuery("MATCH (n{LABEL: 'NCBITaxon'})-[r:IDO_0000664]-(m{LABEL: 'DOID'}) RETURN n.`rdfs:label`, r, m.`rdfs:label`");
        /*m.executeQuery("MATCH (n)-[r]-(m) RETURN COLLECT( distinct n.LABEL ) as DISTINCTNODETYPE, COLLECT(distinct type(r)) as DISTINCTRELTYPE");
        m.executeQuery("MATCH (n{LABEL: 'DOID'})-[r:IDO_0000664]-(m{LABEL: 'NCBITaxon'}) RETURN n.`rdfs:label`, properties(r), m.`rdfs:label`");
        m.executeQuery("MATCH (n{LABEL: 'DOID'})-[r:IDO_0000664]-(m{LABEL: 'NCBITaxon'}) WITH COUNT(r) as rcount RETURN rcount");
        m.executeQuery("MATCH (n{LABEL: 'DOID'})-[r:IDO_0000664]-(m{LABEL: 'NCBITaxon'}) WITH COUNT(distinct(r)) as rcount RETURN rcount");
        m.executeQuery("MATCH (n{LABEL: 'DOID'})-[r:IDO_0000664]-(m{LABEL: 'NCBITaxon'}) WITH COUNT(distinct(r)) as rcount RETURN rcount");
        m.executeQuery("MATCH (n{id: 'DOID_9682'})-[r]-(m) RETURN labels(n), properties(n), properties(m), type(r), properties(r)");
        m.executeQuery("MATCH (p{id:'DOID_0050117'})-[t:isA]-(n{LABEL: 'DOID'})-[r:IDO_0000664]-(m{LABEL: 'NCBITaxon'}) WITH COUNT(distinct(r)) as rcount RETURN rcount");
        m.executeQuery("MATCH (a)<-[:isA*]-(d)" +
                "WHERE a.id='DOID_0050117' WITH COLLECT(distinct d.`rdfs:label`) as DISTINCTINFECTIOUSDISEASE RETURN DISTINCTINFECTIOUSDISEASE, COUNT(DISTINCTINFECTIOUSDISEASE)");
        m.executeQuery("MATCH (a)<-[:isA*]-(d)" +
                "WHERE a.id='DOID_0050117' WITH COUNT(distinct d.`rdfs:label`) as DISTINCTIDCOUNT RETURN DISTINCTIDCOUNT");*/

        m.shutdownDatabase();
        //System.out.println("Success");
    }
}
