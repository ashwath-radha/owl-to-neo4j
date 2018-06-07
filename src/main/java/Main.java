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

    private static File ont = new File("/Users/karthik/Documents/HumanDiseaseOntology/src/ontology/doid-merged.owl");
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
    File graphPath = new File("/Users/karthik/neo4j/pds");
    private GraphDatabaseService graphdb = new GraphDatabaseFactory().newEmbeddedDatabase(graphPath);

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

    private static OWLReasonerFactory reasonerFactory = new StructuralReasonerFactory();
    private static ConsoleProgressMonitor progressMonitor = new ConsoleProgressMonitor();
    private static OWLReasonerConfiguration config = new SimpleConfiguration(progressMonitor);
    private static OWLReasoner reasoner = reasonerFactory.createReasoner(ontology, config);

    public static HashMap<String, String> getRDFSLabel(IRI i){
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
        for(Object j: EntitySearcher.getAnnotations(c, ont, f.getRDFSLabel()).toArray()) {
            OWLAnnotation a = (OWLAnnotation) j;
            System.out.println(a.getValue());
//            if (a.getValue() instanceof OWLLiteral) {
                prop = a.getProperty().toString();
                OWLAnnotationValue val = a.getValue();
                label = ((OWLLiteral) val).getLiteral();
                m.put(prop, label);
//            }
        }
        return m;
    }

    public static void printQuantifiedRestriction( OWLClass oc, OWLQuantifiedObjectRestriction restriction ) {
        /*System.out.println( "\t\tClass: " + oc.toString() );
        System.out.println( "\t\tClassExpressionType: " + restriction.getClassExpressionType().toString() );
        System.out.println( "\t\tProperty: "+ restriction.getProperty().toString() );
        System.out.println( "\t\tObject: " + restriction.getFiller().toString() );*/
        getRDFSLabel(restriction.getProperty().asOWLObjectProperty().getIRI());
        getRDFSLabel(restriction.getFiller().asOWLClass().getIRI());
        System.out.println();
    }

    public static void printCardinalityRestriction( OWLClass oc, OWLObjectCardinalityRestriction restriction ) {
        System.out.println( "\t\tClass: " + oc.toString() );
        System.out.println( "\t\tClassExpressionType: " + restriction.getClassExpressionType().toString() );
        System.out.println( "\t\tCardinality: " + restriction.getCardinality() );
        System.out.println( "\t\tProperty: "+ restriction.getFiller().toString() );
        System.out.println( "\t\tObject: " + restriction.getFiller().toString() );
        System.out.println();
    }

    public void addRelationShip(org.neo4j.graphdb.Node n, org.neo4j.graphdb.Node p, String type){
        try ( Transaction tx = graphdb.beginTx() ) {
            n.createRelationshipTo(p, RelationshipType.withName(type));
            tx.success();
        }
    }

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
                    created.setProperty( "id", properties.get( "id" ) );
                }
            };
            n = gfactory.getOrCreate( "id", id );
            tx.success();
        }

        return n;
    }

    org.neo4j.graphdb.Node relNode;
    String relType;

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
            getRDFSLabel(c.getIRI());
            current = getOrCreateUserWithUniqueFactory(c.getIRI().getFragment(), getRDFSLabel(c.getIRI()));
            /*NodeSet<OWLClass> subCls= reasoner.getSubClasses(c, true);
            for (OWLClass cls : subCls.getFlattened()) {
               //getRDFSLabel(cls.getIRI());
            }*/
            for (OWLClass cls : reasoner.getSuperClasses(c, true).getFlattened()) {
                //getRDFSLabel(cls.getIRI());
                n = getOrCreateUserWithUniqueFactory(cls.getIRI().getFragment(), getRDFSLabel(cls.getIRI()));
                addRelationShip(current, n ,"isA");
            }
/*            for (Object j: ontology.objectPropertiesInSignature().toArray()){
                OWLObjectPropertyExpression objectProperty = (OWLObjectPropertyExpression) j;


            }*/
            for( OWLAxiom axiom : ontology.axioms( c ).collect( Collectors.toSet() ) ) {
                relNode = null;
                relType = null;
                System.out.println( "\tAxiom: " + axiom.toString() );
                final IRI b;
                // create an object visitor to get to the subClass restrictions
                axiom.accept( new OWLObjectVisitor() {

                    // found the subClassOf axiom
                    public void visit( OWLSubClassOfAxiom subClassAxiom ) {

                        // create an object visitor to read the underlying (subClassOf) restrictions
                        subClassAxiom.getSuperClass().accept( new OWLObjectVisitor() {

                            public void visit(OWLClass c){
                                relNode = getOrCreateUserWithUniqueFactory(c.getIRI().getFragment(), getRDFSLabel(c.getIRI()));
                                relType = c.getIRI().getFragment();
                            }

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
                if(relNode != null){
                    addRelationShip(current, relNode, relType);
                }
            }
            ctr += 1;
/*            if(ctr > 50)
                break;*/
        }
    }

    public void shutdownDatabase(){
        graphdb.shutdown();
    }

    public void walkOntology(){
        OWLOntologyWalker walker = new OWLOntologyWalker(Collections.singleton(ontology));
        OWLOntologyWalkerVisitorEx<Object> visitor = new OWLOntologyWalkerVisitorEx<Object>(walker) {

            @Override
            public Object visit(OWLObjectSomeValuesFrom ce) {

                for(Object o: ce.classesInSignature().toArray()){
                    System.out.println("Axiom: " + ce.toString());
                    OWLClass c = (OWLClass) o;
                    System.out.println(ce.getProperty());
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

    public static OWLOntology getOntology() {
        return ontology;
    }

    public static void main(String [] args) throws OWLOntologyCreationException {
        Main m = new Main();
        //m.traverseAllClasses();
        m.executeQuery("MATCH (n)-[r]-(m) RETURN distinct properties(r);");
        m.shutdownDatabase();
    }
}
