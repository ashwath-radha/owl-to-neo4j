import org.json.JSONArray;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.index.UniqueFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.*;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;
import org.semanticweb.owlapi.search.EntitySearcher;

import java.awt.List;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Collectors;
import java.io.FileWriter;
import org.json.JSONObject;


public class owl2neo4j_ash
{
    /* Set up OWL input file, create ontology manager, an empty ontology and the data factory.
    Then set up the file path to store the neo4j graph and create a GraphDatabaseFactory */
    private static File ont = new File("/Users/ARAD/Desktop/College/summer_2018/andersen_lab_stsi/HumanDiseaseOntology/src/ontology/doid-merged.owl");
    private static OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    private static OWLOntology ontology;
    private static OWLDataFactory ontFactory = manager.getOWLDataFactory();

    /* path for storing neo4j database */
    File graphPath = new File("/Users/ARAD/Desktop/neo4j/pds2");
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
    org.neo4j.graphdb.Node targetNode;
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
        //Integer ctr = 0;
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
            System.out.println("Class " + c.toString());
            current = getOrCreateUserWithUniqueFactory(c.getIRI().getFragment(), getRDFSLabel(c.getIRI()));
            //System.out.println("current: " + current.toString());

            for (Object obj : reasoner.getSuperClasses(c, true).entities().toArray())
            {
                OWLClass cls = (OWLClass) obj;
                n = getOrCreateUserWithUniqueFactory(cls.getIRI().getFragment(), getRDFSLabel(cls.getIRI()));
                addRelationShip(current, n ,"isA", null);
                //System.out.println("superClasses " + cls.toString());
            }
            /* the for loop for object property not necessary */

            for( OWLAxiom axiom : ontology.axioms( c ).collect( Collectors.toSet() ) )
            {
                relNode = null;
                targetNode = null;
                relType = null;
                final IRI b;
                // create an object visitor to get to the subClass restrictions
                //System.out.println("Axiom " + axiom.toString());
                axiom.accept(new OWLObjectVisitor()
                {

                    // found the subClassOf axiom
                    public void visit(OWLSubClassOfAxiom subClassAxiom)
                    {
                        // create an object visitor to read the underlying (subClassOf) restrictions
                        subClassAxiom.getSuperClass().accept(new OWLObjectVisitor() {
                            /*public void visit(OWLClass c) {
                                System.out.println("subClassOf " + c.toString());
                                relNode = getOrCreateUserWithUniqueFactory(c.getIRI().getFragment(), getRDFSLabel(c.getIRI()));
                                relIri = c.getIRI();
                                relType = c.getIRI().getFragment();
                            }*/

                            public void visit(OWLObjectSomeValuesFrom someValuesFromAxiom) {
                                //System.out.println("someValuesFromAxiom " + someValuesFromAxiom.toString());
                                relNode = getOrCreateUserWithUniqueFactory(someValuesFromAxiom.getFiller().asOWLClass().getIRI().getFragment(), getRDFSLabel(someValuesFromAxiom.getFiller().asOWLClass().getIRI()));
                                relIri = someValuesFromAxiom.getProperty().asOWLObjectProperty().getIRI();
                                relType = relIri.getFragment();
                                //System.out.println("Relationship: " + relNode + " " + relType + " " + relIri + " " + getRDFSLabel(relIri));

                            }

                            public void visit(OWLObjectIntersectionOf intersectionOfAxiom)
                            {
                                //System.out.println("intersectionOfAxiom " + intersectionOfAxiom.toString());
                                relNode = getOrCreateUserWithUniqueFactory(c.getIRI().getFragment(), getRDFSLabel(c.getIRI()));
                                targetNode = getOrCreateUserWithUniqueFactory(intersectionOfAxiom.getOperandsAsList().get(0).asOWLClass().getIRI().getFragment(), getRDFSLabel(intersectionOfAxiom.getOperandsAsList().get(0).asOWLClass().getIRI()));
                                addRelationShip(relNode, targetNode, "isA", null);
                                //System.out.println("Relationship: " + relNode + " " + targetNode + " isA ");


                                java.util.List osvfList = intersectionOfAxiom.nestedClassExpressions().filter(y->y.getClassExpressionType().toString() == "ObjectSomeValuesFrom").collect(Collectors.toList()); //.collect(Collectors.toList());

                                OWLClass intCls = (OWLClass) (intersectionOfAxiom.nestedClassExpressions().filter(x->x.getClassExpressionType().toString() == "ObjectIntersectionOf").findFirst().get().classesInSignature().toArray()[0]);
                                relNode = getOrCreateUserWithUniqueFactory(intCls.getIRI().getFragment(), getRDFSLabel(intCls.getIRI()));

                                for(Iterator<OWLClassExpression> iter = osvfList.iterator(); iter.hasNext(); )
                                {
                                    OWLClassExpression next = iter.next();

                                    OWLClass objSVFClass = (OWLClass) next.classesInSignature().findFirst().get().classesInSignature().toArray()[0];
                                    targetNode = getOrCreateUserWithUniqueFactory(objSVFClass.getIRI().getFragment(), getRDFSLabel(objSVFClass.getIRI()));

                                    OWLObjectProperty objSVFProp = (OWLObjectProperty) next.objectPropertiesInSignature().toArray()[0];
                                    relIri = objSVFProp.getIRI();
                                    relType = relIri.getFragment();

                                    addRelationShip(relNode, targetNode, relType, getRDFSLabel(relIri));
                                    //System.out.println("Relationship2: " + relNode + " " + targetNode + " " + relType + " " + getRDFSLabel(relIri));

                                }

                                relNode = getOrCreateUserWithUniqueFactory(c.getIRI().getFragment(), getRDFSLabel(c.getIRI()));

                                for(Iterator<OWLClassExpression> iter = osvfList.iterator(); iter.hasNext(); )
                                {
                                    OWLClassExpression next = iter.next();

                                    OWLClass objSVFClass = (OWLClass) next.classesInSignature().findFirst().get().classesInSignature().toArray()[0];
                                    targetNode = getOrCreateUserWithUniqueFactory(objSVFClass.getIRI().getFragment(), getRDFSLabel(objSVFClass.getIRI()));

                                    OWLObjectProperty objSVFProp = (OWLObjectProperty) next.objectPropertiesInSignature().toArray()[0];
                                    relIri = objSVFProp.getIRI();
                                    relType = relIri.getFragment();

                                    addRelationShip(relNode, targetNode, relType, getRDFSLabel(relIri));
                                    //System.out.println("Relationship3: " + relNode + " " + targetNode + " " + relType + " " + getRDFSLabel(relIri));

                                }

                                relNode = null;
                            }

                            //removed methods with calls to printCardinalityRestriction

                        });
                    }


//not reading into these !!! why not!

                    public void visit(OWLEquivalentClassesAxiom equivalentClassAxiom)
                    {
                        //System.out.println("Equivalent Class: "+equivalentClassAxiom.toString());

                        //equivalentClassAxiom.namedClasses().toArray()[0] "isA" equivalentClassAxiom.nestedClassExpressions().filter(y->y.getClassExpressionType().toString() == "ObjectIntersectionOf").forEachOrdered(s->System.out.println(s.classesInSignature().toArray()[0].toString()))
                        OWLClass eqCls = (OWLClass) (equivalentClassAxiom.namedClasses().toArray()[0]);
                        org.neo4j.graphdb.Node eqClsNode = getOrCreateUserWithUniqueFactory(eqCls.getIRI().getFragment(), getRDFSLabel(eqCls.getIRI()));
                        OWLClass intCls = (OWLClass) (equivalentClassAxiom.nestedClassExpressions().filter(x->x.getClassExpressionType().toString() == "ObjectIntersectionOf").findFirst().get().classesInSignature().toArray()[0]);
                        org.neo4j.graphdb.Node intClsNode = getOrCreateUserWithUniqueFactory(intCls.getIRI().getFragment(), getRDFSLabel(intCls.getIRI()));
                        addRelationShip(eqClsNode, intClsNode, "isA", null);
                        //System.out.println("Relationship: " + eqCls.toString() + " " + intCls.toString() + " isA ");



                        java.util.List osvfList = equivalentClassAxiom.nestedClassExpressions().filter(y->y.getClassExpressionType().toString() == "ObjectSomeValuesFrom").collect(Collectors.toList()); //.collect(Collectors.toList());

                        for(Iterator<OWLClassExpression> iter = osvfList.iterator(); iter.hasNext(); )
                        {
                            OWLClassExpression next = iter.next();

                            OWLClass objSVFClass = (OWLClass) next.classesInSignature().findFirst().get().classesInSignature().toArray()[0];
                            targetNode = getOrCreateUserWithUniqueFactory(objSVFClass.getIRI().getFragment(), getRDFSLabel(objSVFClass.getIRI()));

                            OWLObjectProperty objSVFProp = (OWLObjectProperty) next.objectPropertiesInSignature().toArray()[0];
                            relIri = objSVFProp.getIRI();
                            relType = relIri.getFragment();

                            addRelationShip(intClsNode, targetNode, relType, getRDFSLabel(relIri));
                            //System.out.println("Relationship: " + intCls.toString() + " " + objSVFClass.toString() + " " + relType + " " + getRDFSLabel(relIri));

                        }

                        for(Iterator<OWLClassExpression> iter = osvfList.iterator(); iter.hasNext(); )
                        {
                            OWLClassExpression next = iter.next();

                            OWLClass objSVFClass = (OWLClass) next.classesInSignature().findFirst().get().classesInSignature().toArray()[0];
                            targetNode = getOrCreateUserWithUniqueFactory(objSVFClass.getIRI().getFragment(), getRDFSLabel(objSVFClass.getIRI()));

                            OWLObjectProperty objSVFProp = (OWLObjectProperty) next.objectPropertiesInSignature().toArray()[0];
                            relIri = objSVFProp.getIRI();
                            relType = relIri.getFragment();

                            addRelationShip(eqClsNode, targetNode, relType, getRDFSLabel(relIri));
                            //System.out.println("Relationship: " + eqCls.toString() + " " + objSVFClass.toString() + " " + relType + " " + getRDFSLabel(relIri));

                        }
                        relNode = null;
                    }
                });
                //System.out.println();
                if (relNode != null)
                {
                    addRelationShip(current, relNode, relType, getRDFSLabel(relIri));
                    //System.out.println("Relationship: " + current + " " + relNode + " " + relType + " " + getRDFSLabel(relIri));
                }
            }
            //ctr += 1;
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


    public String nodes_for_relation(String rship)
    {
        if(rship == "isA"){
            return ("MATCH (p)-[r]-(q) WHERE properties(r).LABEL = 'isA' RETURN properties(p).`rdfs:label`, properties(q).`rdfs:label`");
        } else{
            return ("MATCH (p)-[r]-(q) WHERE properties(r).`rdfs:label` = '" + rship + "' RETURN properties(p).`rdfs:label`, properties(q).`rdfs:label`" );
        }
    }*/

    public void executeQuery(String query)
    {
        try ( Transaction tx = graphdb.beginTx() )
        {
            int count = 0;
            String rows ="";
            Result result = graphdb.execute( query);
            /*while(result.hasNext())
            {
                Map<String,Object> row = result.next();
                Object rel_value;
                if (count == 0)
                {
                    for (Map.Entry<String, Object> column : row.entrySet()) {
                        //System.out.println("key: " + column.getKey());
                        //System.out.println("value: " + column.getValue());
                        if (column.getKey() == "Relationship") {
                            rel_value = column.getValue();
                        }

                    }
                    for (Map.Entry<String, Object> column : row.entrySet()) {
                        if (column.getKey() != "Relationship" && column.getKey() != "NCBITaxon_label") {
                            rows += column.getKey();
                            rows += ",";
                        }
                        rows += rel_value;
                    }
                }
                for (Map.Entry<String, Object> column : row.entrySet()) {
                    //System.out.println("key: " + column.getKey());
                    //System.out.println("value: " + column.getValue());
                    if (column.getKey() == "Relationship") {
                        rel_value = column.getValue();
                    }

                }
                for (Map.Entry<String, Object> column : row.entrySet()) {
                    if (column.getKey() != "Relationship") {
                        rows += column.getKey();
                        rows += ",";
                    }
                    rows += rel_value;
                }
                rows += "\n";
                count++;
            }*/


            while ( result.hasNext() )
            {
                Map<String,Object> row = result.next();
                if (count == 0)
                {
                    for (Map.Entry<String, Object> column : row.entrySet()) {
                        rows += column.getKey();
                        rows += ",";
                    }
                    rows += "\n";
                }
                for (Map.Entry<String,Object> column : row.entrySet() )
                {
                    rows += column.getValue() + ",";

                }

                //System.out.println("Row: " + rows);
                rows += "\n";
                count++;
                /*if (count > 2000){
                    break;
                }*/
            }

            //write file
            /*try {
                FileWriter writer = new FileWriter("DOID-NCBITaxon_oneway.csv", true);
                writer.write(rows);
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }*/
            System.out.print(rows);
            System.out.println(count);
            tx.success();
        }
    }

    // ex. list and query to return path-dis info
    public void query_for_metagenomic_data(java.lang.String[] taxon_id_list)
    {
        try ( Transaction tx = graphdb.beginTx() )
        {
            try{
                FileWriter writer = new FileWriter("metagenomic_data_db_info.json", true);
                for(Object a: taxon_id_list)
                {
                    String taxon_id = a.toString();
                    String query = "MATCH (m{id: '" + taxon_id + "'})-[r]-(n{LABEL: 'DOID'}) RETURN properties(n).`id` AS DOID, properties(n).`rdfs:label` AS DOID_label, properties(m).`rdfs:label` AS NCBITaxon_label, properties(r).`rdfs:label` AS Property";

                    int count = 0;
                    JSONObject obj = new JSONObject();

                    Result result = graphdb.execute(query);

                    while ( result.hasNext() )
                    {
                        Map<String,Object> row = result.next();
                        JSONObject obj2 = new JSONObject();
                        for (Map.Entry<String,Object> column : row.entrySet() )
                        {
                            //System.out.println("key: " + column.getKey());
                            if(column.getKey().equals("DOID_label"))
                            {
                                obj2.put("DOID_label", column.getValue().toString());
                            }
                            if(column.getKey().equals("NCBITaxon_label"))
                            {
                                obj2.put("NCBITaxon_label", column.getValue().toString());
                            }
                            if(column.getKey().equals("DOID"))
                            {
                                obj2.put("DOID", column.getValue().toString());
                            }
                            if(column.getKey().equals("Property"))
                            {
                                obj2.put("Property", column.getValue().toString());
                            }
                            count++;
                        }
                        obj.put(taxon_id, obj2);
                    }
                    writer.write(obj.toString());
                    writer.write('\n');
                    writer.flush();
                    //System.out.println(count);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            tx.success();
        }
    }


    public static void main(String [] args) //throws OWLOntologyCreationException
    {
        long startTime = System.nanoTime();
        owl2neo4j_ash m = new owl2neo4j_ash();
        //m.traverseAllClasses();
        long endTime   = System.nanoTime();
        long totalTime = endTime - startTime;
        System.out.println("Time to build database: "+ totalTime);

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

        //m.executeQuery("MATCH (m{LABEL: 'DOID'})-[r]-(n) RETURN m.`id`, m.`rdfs:label`");
        //m.executeQuery("MATCH (n{LABEL: 'NCBITaxon'})-[r]-(m{LABEL: 'DOID'}) RETURN n.`rdfs:label`, properties(r).`rdfs:label`, m.`rdfs:label`");
        //m.executeQuery("START m=node(*) MATCH (m{LABEL: 'DOID'})-[r]->(n{LABEL: 'NCBITaxon'}) RETURN CASE WHEN properties(r).LABEL = 'isA' THEN [properties(m).`rdfs:label`, properties(r).LABEL, properties(n).`rdfs:label`] ELSE [properties(m).`rdfs:label`, properties(r).`rdfs:label`, properties(n).`rdfs:label`] END");

        //run these two to eliminate duplicate relationships (from stack overflow)
        //m.executeQuery("START r=relationship(*) MATCH (s{LABEL: 'DOID'})-[r]-(e{LABEL: 'NCBITaxon'}) WITH s,e,type(r) AS typ, tail(collect(r)) AS coll FOREACH(x in coll | delete x)");
        //m.executeQuery("START r=relationship(*) MATCH (s{LABEL: 'DOID'})-[r]->(e) WITH s,e,type(r) AS typ, tail(collect(r)) AS coll FOREACH(x in coll | delete x)");

        //retrieves all DOID->Taxon relationships, MUST check for reverse
        //m.executeQuery("START m=node(*) MATCH (m{LABEL: 'DOID'})-[r]->(n{LABEL: 'NCBITaxon'}) RETURN properties(m).`id` AS DOID, properties(m).`rdfs:label` AS DOID_label, properties(n).`id` AS NCBITaxon, properties(n).`rdfs:label` AS NCBITaxon_label, properties(r).`rdfs:label` AS Relationship ORDER BY properties(r).`rdfs:label`");
        //retrieves all DOID->HP relationships, MUST check for reverse
        //m.executeQuery("START m=node(*) MATCH (m{LABEL: 'DOID'})-[r]-(n{LABEL: 'HP'}) RETURN properties(m).`id` AS DOID, properties(m).`rdfs:label` AS DOID_label, properties(n).`id` AS HP_ID, properties(n).`rdfs:label` AS HP_ID_label, properties(r).`rdfs:label` AS Relationship ORDER BY properties(r).`rdfs:label`");

        //m.executeQuery("START n=node(*) RETURN n"); //all nodes
        //errors...m.executeQuery("START n=node(*) MATCH (n)-[r]->(m) RETURN n,r,m"); //all relationships

        //m.executeQuery("MATCH (m{LABEL: 'DOID'}) RETURN properties(m).`rdfs:label`"); // 11191
        //m.executeQuery("MATCH (m{LABEL: 'NCBITaxon'}) RETURN properties(m).`rdfs:label`"); // 887
        //m.executeQuery("START m=node(*) MATCH (m{LABEL: 'DOID'})-[r]-(n{LABEL: 'HP'}) RETURN properties(m).`rdfs:label`, properties(n).`rdfs:label`"); // 491
        //m.executeQuery("MATCH (m{LABEL: 'DOID'})-[r]-(n{LABEL: 'HP'}) RETURN properties(m).`rdfs:label`, properties(n).`rdfs:label`"); // 491

        //m.executeQuery("START m=node(*) MATCH (m{LABEL: 'DOID'})-[r]-(n{LABEL: 'NCBITaxon'}) RETURN properties(m).`rdfs:label`, properties(n).`rdfs:label`"); // 388
        //m.executeQuery("MATCH (m{LABEL: 'DOID'})-[r]-(n{LABEL: 'NCBITaxon'}) RETURN properties(m).`rdfs:label`, properties(n).`rdfs:label`"); // 388
        //m.executeQuery("MATCH (m{LABEL: 'DOID'})-[r]->(n{LABEL: 'NCBITaxon'}) RETURN properties(m).`rdfs:label`, properties(n).`rdfs:label`"); // 373
        //m.executeQuery("MATCH (m{LABEL: 'DOID'})<-[r]-(n{LABEL: 'NCBITaxon'}) RETURN properties(m).`rdfs:label`, properties(n).`rdfs:label`"); // 15

        //m.executeQuery("MATCH (m{LABEL: 'HP'})-[r]-(n{LABEL: 'NCBITaxon'}) RETURN properties(m), properties(n), properties(r)"); // 0

        //m.executeQuery("MATCH (m{`rdfs:label`: 'liver disease'})-[r]-(n) RETURN properties(m), properties(n), properties(r)");

        // ex. list and query to return path-dis info
        String[] input = {"NCBITaxon_186538", "NCBITaxon_7158", "NCBITaxon_30639", "NCBITaxon_11620", "NCBITaxon_11552"};
        //m.query_for_metagenomic_data(input);

        m.shutdownDatabase();
        System.out.println("Success");
    }
}