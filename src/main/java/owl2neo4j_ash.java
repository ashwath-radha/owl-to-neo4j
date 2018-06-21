import org.neo4j.graphdb.*;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.index.UniqueFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.*;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;
import org.semanticweb.owlapi.search.EntitySearcher;


import java.io.File;
import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Collectors;

public class owl2neo4j_ash
{
    /* Set up OWL input file, create ontology manager, an empty ontology and the data factory.
    Then set up the file path to store the neo4j graph and create a GraphDatabaseFactory */
    private static File ont = new File("/Users/ARAD/Desktop/College/summer_2018/andersen_lab_stsi/HumanDiseaseOntology/src/ontology/doid-merged.owl");
    private static OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    private static OWLOntology ontology;
    private static OWLDataFactory ontFactory = manager.getOWLDataFactory();

    /* path for storing neo4j database */
    File graphPath = new File("/Users/ARAD/Desktop/neo4j/pds");
    private GraphDatabaseService graphdb = new GraphDatabaseFactory().newEmbeddedDatabase(graphPath);

    /* Check for exceptions when loading ontology */
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
    public static HashMap<String, String> getRDFSLabel(IRI i)
    {
        String iri;// = i.toString();

        if(i != null){
            iri = i.toString();
        }else{
            OWLClass clazz = manager.getOWLDataFactory().getOWLThing();
            IRI j = clazz.getIRI();
            iri = j.toString();
        }

        String prop, label;
        OWLClass c;

        // removed local variables for ontFactory and ontology
        HashMap<String, String> m = new HashMap<>();

        c = ontFactory.getOWLClass(iri);

        for (Object j : EntitySearcher.getAnnotations(c, ontology, ontFactory.getRDFSLabel()).toArray())
        {
            OWLAnnotation a = (OWLAnnotation) j;
            //System.out.println(((OWLLiteral) (a.getValue())).getLiteral());
            prop = a.getProperty().toString();
            OWLAnnotationValue val = a.getValue();
            label = ((OWLLiteral) val).getLiteral();
            m.put(prop, label);
        }
        return m;
    }
    // removed printQuantifiedRestriction and printCardinalityRestriction methods

    public void addRelationShip(org.neo4j.graphdb.Node n, org.neo4j.graphdb.Node p, String type, HashMap<String, String> m)
    {
        try ( Transaction tx = graphdb.beginTx() )
        {
            Relationship r = n.createRelationshipTo(p, RelationshipType.withName(type));
            if(m!=null)
            {
                for (Map.Entry<String, String> entry : m.entrySet())
                {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    r.setProperty(key, value);
                }
            }
            r.setProperty("LABEL", type.split("_")[0]);
            tx.success();
        }
    }

    public void executeQuery(String query)
    {
        try ( Transaction tx = graphdb.beginTx() )
        {
            int count = 0;
            String rows ="";
            Result result = graphdb.execute( query);
            while ( result.hasNext() )
            {
                Map<String,Object> row = result.next();
                for (Map.Entry<String,Object> column : row.entrySet() )
                {
                    rows += column.getValue() + ";";
                    //rows += column.getKey() + ": " + column.getValue() + "; ";
                }
                rows += "\n";
                count++;
                if (count > 2000){
                    break;
                }
            }
            System.out.print(rows);
            tx.success();
        }
    }

    /*
    getOrCreate... method allows us to retrieve a neo4j node for our graph database.
    We override the initialize method for gfactory. It works off of the original parameter
    m, traverses that hashmap, and setProperty for key/value on the node created.
    More details on the initialize override: https://neo4j.com/docs/java-reference/current/tutorials-java-embedded/
     */

    public org.neo4j.graphdb.Node getOrCreateUserWithUniqueFactory(String id, HashMap<String, String> m)
    {
        org.neo4j.graphdb.Node n;
        try ( Transaction tx = graphdb.beginTx() )
        {
            // Database operations go here
            UniqueFactory<org.neo4j.graphdb.Node> gfactory = new UniqueFactory.UniqueNodeFactory(graphdb, "Nodes")
            {
                @Override
                protected void initialize(org.neo4j.graphdb.Node created, Map<String, Object> properties)
                {
                    for (Map.Entry<String, String> entry : m.entrySet())
                    {
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

    org.neo4j.graphdb.Node relNode;
    String relType;
    IRI relIri;

    /*
    traverseAllClasses() is the most important method in the class. It is traversing all the classes,
    then looking into each's superClasses and Axioms (subClasses and equivalentClasses) and adding the
    correct relationships between these entities. As shown in the Eclipse XML editor, there is an IRI
    and then a Class and Axiom label. The sub-labels for the Class label are also "axioms." If the
    subClassOf label doesn't have a Restriction sub-label, then it is modeled with the isA
    relationship. Else, we have to traverse the axioms with a visitor nd pull out the
    property that describes the relationship.
     */

    public void traverseAllClasses()
    {
        Integer ctr = 0;
        org.neo4j.graphdb.Node current, n;

        reasoner.precomputeInferences();

        for(Object o: ontology.classesInSignature().toArray())
        {
            try ( Transaction tx = graphdb.beginTx() )
            {
                // Database operations go here
                tx.success();
            }
            OWLClass c = (OWLClass) o;
            current = getOrCreateUserWithUniqueFactory(c.getIRI().getFragment(), getRDFSLabel(c.getIRI()));

            for (Object obj : reasoner.getSuperClasses(c, true).entities().toArray()){
                OWLClass cls = (OWLClass) obj;
                n = getOrCreateUserWithUniqueFactory(cls.getIRI().getFragment(), getRDFSLabel(cls.getIRI()));
                addRelationShip(current, n ,"isA", null);
            }
            /* the for loop for object property not necessary */

            for( OWLAxiom axiom : ontology.axioms( c ).collect( Collectors.toSet() ) ) {
                relNode = null;
                relType = null;
                final IRI b;
                // create an object visitor to get to the subClass restrictions
                axiom.accept( new OWLObjectVisitor() {

                    // found the subClassOf axiom
                    public void visit( OWLSubClassOfAxiom subClassAxiom )
                    {
                        // create an object visitor to read the underlying (subClassOf) restrictions
                        subClassAxiom.getSuperClass().accept( new OWLObjectVisitor()
                        {
                            public void visit(OWLClass c)
                            {
                                relNode = getOrCreateUserWithUniqueFactory(c.getIRI().getFragment(), getRDFSLabel(c.getIRI()));
                                relType = c.getIRI().getFragment();
                            }

                            public void visit( OWLObjectSomeValuesFrom someValuesFromAxiom )
                            {
                                relNode = getOrCreateUserWithUniqueFactory(someValuesFromAxiom.getFiller().asOWLClass().getIRI().getFragment(), getRDFSLabel(someValuesFromAxiom.getFiller().asOWLClass().getIRI()));
                                relIri = someValuesFromAxiom.getProperty().asOWLObjectProperty().getIRI();
                                relType = relIri.getFragment();
                            }

                            //removed methods with calls to printCardinalityRestriction

                        });
                    }

                    public void visit( OWLEquivalentClassesAxiom equivalentClassAxiom )
                    {
                        // create an object visitor to read the underlying (subClassOf) restrictions
                        equivalentClassAxiom.accept( new OWLObjectVisitor()
                        {
                            public void visit(OWLClass c)
                            {
                                relNode = getOrCreateUserWithUniqueFactory(c.getIRI().getFragment(), getRDFSLabel(c.getIRI()));
                                relType = c.getIRI().getFragment();
                            }

                            public void visit( OWLObjectSomeValuesFrom someValuesFromAxiom )
                            {
                                relNode = getOrCreateUserWithUniqueFactory(someValuesFromAxiom.getFiller().asOWLClass().getIRI().getFragment(), getRDFSLabel(someValuesFromAxiom.getFiller().asOWLClass().getIRI()));
                                relIri = someValuesFromAxiom.getProperty().asOWLObjectProperty().getIRI();
                                relType = relIri.getFragment();
                            }

                            //removed methods with calls to printCardinalityRestriction and printQuantifiedRestriction
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

    public static OWLOntology getOntology()
    {
        return ontology;
    }

    /*
    Method that takes in the name of a potential relationship between two nodes
    and returns all the pairs that have this relationship. The user must input the
    exact name of the relationship (ex. 'has material basis in', 'complicated_by').
    Will use executeQuery method to break down Cypher queries that traverse neo4j database.
    */

    public String nodes_for_relation(String rship)
    {
        if(rship == "isA"){
            return ("MATCH (p)-[r]-(q) WHERE properties(r).LABEL = 'isA' RETURN properties(p).`rdfs:label`, properties(q).`rdfs:label`");
        } else{
            return ("MATCH (p)-[r]-(q) WHERE properties(r).`rdfs:label` = '" + rship + "' RETURN properties(p).`rdfs:label`, properties(q).`rdfs:label`" );
        }
    }


    public static void main(String [] args) throws OWLOntologyCreationException
    {
        long startTime = System.nanoTime();
        owl2neo4j_ash m = new owl2neo4j_ash();
        //m.traverseAllClasses();
        //long endTime   = System.nanoTime();
        //long totalTime = endTime - startTime;
        //System.out.println("Time to build database: "+ totalTime);

        //m.executeQuery("MATCH (n{LABEL: 'NCBITaxon'})-[r:IDO_0000664]-(m{LABEL: 'DOID'}) RETURN n.`rdfs:label`, r, m.`rdfs:label`");
        //m.executeQuery("MATCH (n)-[r]-(m) RETURN COLLECT( distinct n.LABEL ) as DISTINCTNODETYPE, COLLECT(distinct type(r)) as DISTINCTRELTYPE");
        //m.executeQuery("MATCH (n{LABEL: 'DOID'})-[r:IDO_0000664]-(m{LABEL: 'NCBITaxon'}) RETURN n.`rdfs:label`, properties(r), m.`rdfs:label`");
        //m.executeQuery("MATCH (n{LABEL: 'DOID'})-[r:IDO_0000664]-(m{LABEL: 'NCBITaxon'}) WITH COUNT(r) as rcount RETURN rcount");
        //m.executeQuery("MATCH (n{LABEL: 'DOID'})-[r:IDO_0000664]-(m{LABEL: 'NCBITaxon'}) WITH COUNT(distinct(r)) as rcount RETURN rcount");
        //m.executeQuery("MATCH (n{id: 'DOID_9682'})-[r]-(m) RETURN labels(n), properties(n), properties(m), type(r), properties(r)");
        //m.executeQuery("MATCH (p{id:'DOID_0050117'})-[t:isA]-(n{LABEL: 'DOID'})-[r:IDO_0000664]-(m{LABEL: 'NCBITaxon'}) WITH COUNT(distinct(r)) as rcount RETURN rcount");
        //m.executeQuery("MATCH (a)<-[:isA*]-(d)" +"WHERE a.id='DOID_0050117' WITH COLLECT(distinct d.`rdfs:label`) as DISTINCTINFECTIOUSDISEASE RETURN DISTINCTINFECTIOUSDISEASE, COUNT(DISTINCTINFECTIOUSDISEASE)");
        //m.executeQuery("MATCH (a)<-[:isA*]-(d)" +"WHERE a.id='DOID_0050117' WITH COUNT(distinct d.`rdfs:label`) as DISTINCTIDCOUNT RETURN DISTINCTIDCOUNT");

        //m.executeQuery("MATCH (p)-[r]-(q) WHERE properties(r).LABEL <> 'isA' RETURN properties(p), properties(q), properties(r)");
        //m.executeQuery("MATCH (p)-[r]-(q) RETURN properties(r)");
        //m.executeQuery("MATCH (p)-[r]-(q) RETURN properties(r).LABEL, properties(r).`rdfs:label`");
        //m.executeQuery("MATCH (p)-[r]-(q) RETURN CASE WHEN properties(r).LABEL = 'isA' THEN properties(r).LABEL ELSE properties(r).`rdfs:label` END");

        //m.executeQuery(m.nodes_for_relation("has material basis in"));
        //m.executeQuery("START m=node(*) MATCH (m)-[r]->(n) RETURN properties(n).`rdfs:label`, properties(r).LABEL, properties(m).`rdfs:label`");

        m.shutdownDatabase();
        System.out.println("Success");
    }
}