package probe;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import net.sourceforge.gxl.GXLAtomicValue;
import net.sourceforge.gxl.GXLAttr;
import net.sourceforge.gxl.GXLAttributedElement;
import net.sourceforge.gxl.GXLDocument;
import net.sourceforge.gxl.GXLEdge;
import net.sourceforge.gxl.GXLElement;
import net.sourceforge.gxl.GXLFloat;
import net.sourceforge.gxl.GXLGraph;
import net.sourceforge.gxl.GXLInt;
import net.sourceforge.gxl.GXLNode;

/** Reads a call graph from a GXL file. */
public class GXLReader {
    /** Read a call graph from a GXL file. */
    public CallGraph readCallGraph( InputStream file ) throws IOException {
        uri = new URIs( "/~olhotak/probe/schemas/callgraph.gxl" );
        getGraph(file, "callgraph");
        readNodesEdges();
        sortNodes();
        sortEdges();
        createNodeMaps();

        CallGraph ret = new CallGraph();

        for( Iterator nodeIt = entryPoints.iterator(); nodeIt.hasNext(); ) {

            final GXLNode node = (GXLNode) nodeIt.next();
            ret.entryPoints().add( (ProbeMethod) nodeToMethod.get(node) );
        }
        for( Iterator edgeIt = callEdges.iterator(); edgeIt.hasNext(); ) {
            final GXLEdge edge = (GXLEdge) edgeIt.next();
            if( hasAttr(edge, "weight") ) {
                ret.edges().add( new CallEdge(
                    (ProbeMethod) nodeToMethod.get(edge.getSource()),
                    (ProbeMethod) nodeToMethod.get(edge.getTarget()),
                    getDouble(edge, "weight")));
            } else {
                ret.edges().add( new CallEdge(
                    (ProbeMethod) nodeToMethod.get(edge.getSource()),
                    (ProbeMethod) nodeToMethod.get(edge.getTarget())));
            }
        }

        return ret;
    }

    /** Read a set of recursive methods from a GXL file. */
    public Recursive readRecursive( InputStream file ) throws IOException {
        uri = new URIs( "/~olhotak/probe/schemas/recursive.gxl" );
        getGraph(file, "recursive");
        readNodesEdges();
        sortNodes();
        sortEdges();
        createNodeMaps();

        Recursive ret = new Recursive();

        for( Iterator nodeIt = methods.iterator(); nodeIt.hasNext(); ) {

            final GXLNode node = (GXLNode) nodeIt.next();
            ret.methods().add( nodeToMethod.get(node) );
        }

        return ret;
    }

    /** Read a set of allocation sites that execute more than once. */
    public ExecutesMany readExecutesMany( InputStream file ) throws IOException {
        uri = new URIs( "/~olhotak/probe/schemas/executesmany.gxl" );
        getGraph(file, "executesmany");
        readNodesEdges();
        sortNodes();
        sortEdges();
        createNodeMaps();

        ExecutesMany ret = new ExecutesMany();

        for( Iterator nodeIt = stmts.iterator(); nodeIt.hasNext(); ) {

            final GXLNode node = (GXLNode) nodeIt.next();
            ret.stmts().add( nodeToStmt.get(node) );
        }

        return ret;
    }

    /** Read a set of allocation sites that execute more than once. */
    public Polymorphic readPolymorphic( InputStream file ) throws IOException {
        uri = new URIs( "/~olhotak/probe/schemas/polymorphic.gxl" );
        getGraph(file, "polymorphic");
        readNodesEdges();
        sortNodes();
        sortEdges();
        createNodeMaps();

        Polymorphic ret = new Polymorphic();

        for( Iterator nodeIt = stmts.iterator(); nodeIt.hasNext(); ) {

            final GXLNode node = (GXLNode) nodeIt.next();
            ret.stmts().add( nodeToStmt.get(node) );
        }

        return ret;
    }

    /** Read a set of cast instructions that fail. */
    public FailCast readFailCast( InputStream file ) throws IOException {
        uri = new URIs( "/~olhotak/probe/schemas/failcast.gxl" );
        getGraph(file, "failcast");
        readNodesEdges();
        sortNodes();
        sortEdges();
        createNodeMaps();

        FailCast ret = new FailCast();

        for( Iterator nodeIt = fails.iterator(); nodeIt.hasNext(); ) {

            final GXLNode node = (GXLNode) nodeIt.next();
            ret.stmts().add( nodeToStmt.get(node) );
        }
        for( Iterator nodeIt = executes.iterator(); nodeIt.hasNext(); ) {
            final GXLNode node = (GXLNode) nodeIt.next();
            ret.executes().add( nodeToStmt.get(node) );
        }

        return ret;
    }

    /** Read a set of allocation sites whose objects escape their allocating
     * method/thread. */
    public Escape readEscape( InputStream file ) throws IOException {
        uri = new URIs( "/~olhotak/probe/schemas/escape.gxl" );
        getGraph(file, "escape");
        readNodesEdges();
        sortNodes();
        sortEdges();
        createNodeMaps();

        Escape ret = new Escape();

        for( Iterator nodeIt = escapesThread.iterator(); nodeIt.hasNext(); ) {

            final GXLNode node = (GXLNode) nodeIt.next();
            ret.escapesThread().add( nodeToStmt.get(node) );
        }
        for( Iterator nodeIt = escapesMethod.iterator(); nodeIt.hasNext(); ) {
            final GXLNode node = (GXLNode) nodeIt.next();
            ret.escapesMethod().add( nodeToStmt.get(node) );
        }
        for( Iterator nodeIt = anyAlloc.iterator(); nodeIt.hasNext(); ) {
            final GXLNode node = (GXLNode) nodeIt.next();
            ret.anyAlloc().add( nodeToStmt.get(node) );
        }

        return ret;
    }

    /** Read points-to information. */
    public PointsTo readPointsTo( InputStream file ) throws IOException {
        uri = new URIs( "/~olhotak/probe/schemas/pointsto.gxl" );
        getGraph(file, "pointsto");
        readNodesEdges();
        sortNodes();
        sortEdges();
        createNodeMaps();
        createSetMaps();

        PointsTo ret = new PointsTo();

        for( Iterator nodeIt = stmts.iterator(); nodeIt.hasNext(); ) {

            final GXLNode node = (GXLNode) nodeIt.next();
            ret.pointsTo().put( node, nodeToPtSet.get(pointsTo.get(node)) );
        }
        for( Iterator nodeIt = parameters.iterator(); nodeIt.hasNext(); ) {
            final GXLNode node = (GXLNode) nodeIt.next();
            ret.pointsTo().put( node, nodeToPtSet.get(pointsTo.get(node)) );
        }

        return ret;
    }

    /** Read side-effect information. */
    public SideEffect readSideEffect( InputStream file ) throws IOException {
        uri = new URIs( "/~olhotak/probe/schemas/sideeffect.gxl" );
        getGraph(file, "sideeffect");
        readNodesEdges();
        sortNodes();
        sortEdges();
        createNodeMaps();
        createSetMaps();

        SideEffect ret = new SideEffect();

        for( Iterator nodeIt = stmts.iterator(); nodeIt.hasNext(); ) {

            final GXLNode node = (GXLNode) nodeIt.next();
            ret.reads().put( node, nodeToFieldSet.get(reads.get(node)) );
            ret.writes().put( node, nodeToFieldSet.get(writes.get(node)) );
        }

        return ret;
    }
    
    /** Read a call graph from a text file. */
	public CallGraph readCallGraph(String file) throws IOException {
		if (file.endsWith("gxl.gzip")) {
			return readCallGraph(new GZIPInputStream(new FileInputStream(file)));
		} else if (file.endsWith("gxl")) {
			return readCallGraph(new FileInputStream(file));
		} else if (file.endsWith("txt.gzip") || file.endsWith("txt")) {
			throw new IOException(
					"Cannot process txt.gzip files. Please use probe.TextReader for this file.");
		} else {
			throw new IOException("undefined file extension.");
		}
	}

    /* End of public methods. */

    private URIs uri;

    private GXLDocument gxlDocument;
    private GXLGraph graph;
    private void getGraph(InputStream file, String graphName) {
        gxlDocument = null;
        try {
            gxlDocument = new GXLDocument(file);
        } catch( Exception e ) {
            throw new RuntimeException( "Caught exception in parsing: "+e );
        }
        graph = (GXLGraph) gxlDocument.getElement(graphName);
    }

    private List edges = new ArrayList();
    private List nodes = new ArrayList();
    private void readNodesEdges() {
        for( int i = 0; i < graph.getGraphElementCount(); i++ ) {
            GXLElement elem = graph.getGraphElementAt(i);
            if( elem instanceof GXLNode ) {
                nodes.add(elem);
            } else if( elem instanceof GXLEdge ) {
                edges.add(elem);
            } else {
                throw new RuntimeException( "unrecognized graph element "+elem );
            }
        }
    }

    private GXLNode root;
    private Collection classes = new HashSet();
    private Collection methods = new HashSet();
    private Collection fields = new HashSet();
    private Collection stmts = new HashSet();
    private Collection parameters = new HashSet();
    private Collection ptsets = new HashSet();
    private Collection fieldsets = new HashSet();
    private void sortNodes() {
        for( Iterator nodeIt = nodes.iterator(); nodeIt.hasNext(); ) {
            final GXLNode node = (GXLNode) nodeIt.next();
            if( eqURI(node.getType().getURI(), uri.uRoot() ) ) {
                root = node;
            } else if( eqURI(node.getType().getURI(), uri.uClass() ) ) {
                classes.add(node);
            } else if( eqURI(node.getType().getURI(), uri.uMethod() ) ) {
                methods.add(node);
            } else if( eqURI(node.getType().getURI(), uri.uField() ) ) {
                fields.add(node);
            } else if( eqURI(node.getType().getURI(), uri.uStmt() ) ) {
                stmts.add(node);
            } else if( eqURI(node.getType().getURI(), uri.uParameter() ) ) {
                parameters.add(node);
            } else if( eqURI(node.getType().getURI(), uri.uPtSet() ) ) {
                ptsets.add(node);
            } else if( eqURI(node.getType().getURI(), uri.uFieldSet() ) ) {
                fieldsets.add(node);
            } else {
                throw new RuntimeException( "unrecognized node "+node+"; its id is "+node.getID() );
            }
        }
    }

    private List entryPoints = new ArrayList();
    private List callEdges = new ArrayList();
    private List fails = new ArrayList();
    private List executes = new ArrayList();
    private List escapesThread = new ArrayList();
    private List escapesMethod = new ArrayList();
    private List anyAlloc = new ArrayList();
    private Map declaredIn = new HashMap();
    private Map inBody = new HashMap();
    private Map pointsTo = new HashMap();
    private Map reads = new HashMap();
    private Map writes = new HashMap();
    private List inSet = new ArrayList();
    private boolean eqURI(java.net.URI u1, java.net.URI u2) {
        return u1.getFragment().equals(u2.getFragment());
    }
    private void sortEdges() {
        for( Iterator edgeIt = edges.iterator(); edgeIt.hasNext(); ) {
            final GXLEdge edge = (GXLEdge) edgeIt.next();
            GXLNode src = (GXLNode) edge.getSource();
            GXLNode dst = (GXLNode) edge.getTarget();
            if( eqURI(edge.getType().getURI(), uri.declaredIn() ) ) {
                declaredIn.put(src, dst);
            } else if( eqURI(edge.getType().getURI(), uri.inBody() ) ) {
                inBody.put(src, dst);
            } else if( eqURI(edge.getType().getURI(), uri.pointsTo() ) ) {
                pointsTo.put(src, dst);
            } else if( eqURI(edge.getType().getURI(), uri.reads() ) ) {
                reads.put(src, dst);
            } else if( eqURI(edge.getType().getURI(), uri.writes() ) ) {
                writes.put(src, dst);
            } else if( eqURI(edge.getType().getURI(), uri.entryPoint() ) ) {
                entryPoints.add(dst);
            } else if( eqURI(edge.getType().getURI(), uri.escapesThread() ) ) {
                escapesThread.add(dst);
            } else if( eqURI(edge.getType().getURI(), uri.anyAlloc() ) ) {
                anyAlloc.add(dst);
            } else if( eqURI(edge.getType().getURI(), uri.escapesMethod() ) ) {
                escapesMethod.add(dst);
            } else if( eqURI(edge.getType().getURI(), uri.fails() ) ) {
                fails.add(dst);
            } else if( eqURI(edge.getType().getURI(), uri.executes() ) ) {
                executes.add(dst);
            } else if( eqURI(edge.getType().getURI(), uri.calls() ) ) {
                callEdges.add(edge);
            } else if( eqURI(edge.getType().getURI(), uri.inSet() ) ) {
                inSet.add(edge);
            } else {
                throw new RuntimeException( "unrecognized edge "+edge+"; its id is "+edge.getID() );
            }
        }
    }

    private Map nodeToClass = new HashMap();
    private Map nodeToMethod = new HashMap();
    private Map nodeToField = new HashMap();
    private Map nodeToStmt = new HashMap();
    private Map nodeToParameter = new HashMap();
    private Map nodeToPtSet = new HashMap();
    private Map nodeToFieldSet = new HashMap();
    private void createNodeMaps() {
        for( Iterator nodeIt = classes.iterator(); nodeIt.hasNext(); ) {
            final GXLNode node = (GXLNode) nodeIt.next();
            nodeToClass.put( node, ObjectManager.v().getClass(
                        getString(node, "package"),
                        getString(node, "name")));
        }

        for( Iterator nodeIt = methods.iterator(); nodeIt.hasNext(); ) {

            final GXLNode node = (GXLNode) nodeIt.next();
            GXLNode classNode = (GXLNode) declaredIn.get(node);
            nodeToMethod.put( node, ObjectManager.v().getMethod(
                        (ProbeClass) nodeToClass.get(classNode),
                        getString(node, "name"),
                        getString(node, "signature")));
        }

        for( Iterator nodeIt = fields.iterator(); nodeIt.hasNext(); ) {

            final GXLNode node = (GXLNode) nodeIt.next();
            GXLNode classNode = (GXLNode) declaredIn.get(node);
            nodeToMethod.put( node, ObjectManager.v().getField(
                        (ProbeClass) nodeToClass.get(classNode),
                        getString(node, "name")));
        }
        
        for( Iterator nodeIt = stmts.iterator(); nodeIt.hasNext(); ) {
        
            final GXLNode node = (GXLNode) nodeIt.next();
            GXLNode methodNode = (GXLNode) inBody.get(node);
            nodeToStmt.put( node, ObjectManager.v().getStmt(
                        (ProbeMethod) nodeToMethod.get(methodNode),
                        getInt(node, "offset")));
        }
    }

    private void createSetMaps() {
        Map setToElements = new HashMap();
        for( Iterator nodeIt = ptsets.iterator(); nodeIt.hasNext(); ) {
            final GXLNode node = (GXLNode) nodeIt.next();
            setToElements.put(node, new ArrayList());
        }
        for( Iterator nodeIt = fieldsets.iterator(); nodeIt.hasNext(); ) {
            final GXLNode node = (GXLNode) nodeIt.next();
            setToElements.put(node, new ArrayList());
        }
        for( Iterator eIt = inSet.iterator(); eIt.hasNext(); ) {
            final GXLEdge e = (GXLEdge) eIt.next();
            List l = (List) setToElements.get(e.getTarget());
            l.add(e.getSource());
        }
        for( Iterator nodeIt = ptsets.iterator(); nodeIt.hasNext(); ) {
            final GXLNode node = (GXLNode) nodeIt.next();
            nodeToPtSet.put( node,
                    new ProbePtSet( (List) setToElements.get(node) ) );
        }
        for( Iterator nodeIt = fieldsets.iterator(); nodeIt.hasNext(); ) {
            final GXLNode node = (GXLNode) nodeIt.next();
            nodeToFieldSet.put( node,
                new ProbeFieldSet( (List) setToElements.get(node) ) );
        }
    }

    private String getString( GXLAttributedElement elem, String key ) {
        GXLAttr attr = elem.getAttr(key);
        GXLAtomicValue value = (GXLAtomicValue) attr.getValue();
        return value.getValue();
    }

    private int getInt( GXLAttributedElement elem, String key ) {
        GXLAttr attr = elem.getAttr(key);
        GXLInt value = (GXLInt) attr.getValue();
        return value.getIntValue();
    }

    private double getDouble( GXLAttributedElement elem, String key ) {
        GXLAttr attr = elem.getAttr(key);
        GXLFloat value = (GXLFloat) attr.getValue();
        return value.getFloatValue();
    }

    private boolean hasAttr( GXLAttributedElement elem, String key ) {
        GXLAttr attr = elem.getAttr(key);
        return attr != null;
    }
}
