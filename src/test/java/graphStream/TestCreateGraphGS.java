package graphStream;

import java.util.Iterator;

import org.graphstream.algorithm.generator.DorogovtsevMendesGenerator;
import org.graphstream.algorithm.generator.Generator;
import org.graphstream.algorithm.generator.GridGenerator;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.ui.fx_viewer.FxViewer;
import org.graphstream.ui.spriteManager.SpriteManager;
import org.graphstream.ui.view.Viewer;

/**
 * This class is an example allowing you to quickly obtain a graphical representation of a given graph using the graphStream library. 
 * It can be useful in order to follow an agent's view of its knowledge of the world
 * @author hc
 *
 */
public class TestCreateGraphGS {

	public static void main(String[] args) {
		
		//color of a node according to its type
		//String defaultNodeStyle= "node {"+"text-size:20;fill-color: black;"+" size-mode:fit;text-alignment:right;text-color:white;text-background-mode:rounded-box;text-background-color:black;}";
		String defaultNodeStyle= "node {"+"fill-color: white;"+"text-size:20;size-mode:fit;text-alignment:center;text-color:black;text-background-mode:rounded-box;text-background-color:white;}";
		//String nodeStyle_wumpus= "node.wumpus {"+"fill-color: red;text-background-color:red;"+"}";
		String nodeStyle_wumpus= "node.wumpus {"+"text-alignment:under;size:10;text-background-color:white;fill-color: red;"+"}";
		//String nodeStyle_agent= "node.agent {"+"text-size:40;fill-color: forestgreen;text-alignment:center;text-background-color:forestgreen;"+"}";
		String nodeStyle_agent= "node.agent {"+"text-alignment:under;size:10;text-background-color:white;fill-color:blue;"+"}";
		//String nodeStyle_treasure="node.treasure {"+"size-mode:normal;size:20;shape:cross;fill-color: yellow;"+"}";
		String nodeStyle_treasure= "node.treasure {"+"size-mode:normal;fill-color: yellow;shape:cross;size:40;text-background-color:yellow;"+"}";
		//String nodeStyle_EntryExit="node.exit {"+"text-alignment:under;size:10;text-background-color:white;fill-color: green;"+"}";
		String nodeStyle_EntryExit="node.exit {size-mode:normal;text-alignment:under;fill-mode: image-scaled;size:40; fill-image: url('src/test/java/graphStream/deathStar1.png');}";
		//
		String nodeStyle=defaultNodeStyle+nodeStyle_wumpus+nodeStyle_agent+nodeStyle_treasure+nodeStyle_EntryExit;
				
		//System.setProperty("org.graphstream.ui", "swing");//working with graph.display but not with new FxViewer
		System.setProperty("org.graphstream.ui", "javafx");
		System.setProperty("org.graphstream.debug","true");//required on some systems otherwise it generate a bug. See FxVewer.class line 200
		
		Graph graph = new SingleGraph("Illustrative example");
	
		Iterator<Node> iter=graph.iterator();
		
		graph.setAttribute("ui.stylesheet",nodeStyle);
		
		Viewer viewer = graph.display();
		
		SpriteManager sman = new SpriteManager(graph);

		// the nodes can be added dynamically.
		graph.addNode("A");
		Node n= graph.getNode("A");
		n.setAttribute("ui.label", "Agent Nemar");	//ui.label is the label used for display. You can create your own labels but they will not be displayed
		n.setAttribute("ui.class", "agent");// used to define the node rendering (color/form/..)
		
		//The content of a node can be accessed by calling its attribute(s). 
		//You
		Object o=n.getAttribute("ui.label");
		System.out.println("object: "+o.toString());
		
		graph.addNode("B");
		n= graph.getNode("B");
		n.setAttribute("ui.label", "treasure");	
		n.setAttribute("ui.class", "treasure");
		
		graph.addNode("C");	
		n= graph.getNode("C");
		n.setAttribute("ui.label", "wumpus");	
		n.setAttribute("ui.class", "wumpus");
		
		graph.addNode("D");
		n= graph.getNode("D");
		n.setAttribute("ui.label", "Small moon");	
		n.setAttribute("ui.class", "exit");
		
		graph.addNode("E");

		//graph structure
		graph.addEdge("AB", "A", "B");
		graph.addEdge("BC", "B", "C");
		graph.addEdge("CA", "C", "A");
		graph.addEdge("DA", "D", "A");
		graph.addEdge("EC", "E", "C");
		
		
//		
//		Sprite s=sman.addSprite("s1"); //sprite name
//		//s.setPosition(2, 1, 0); //sprite relative position
//		s.attachToNode("C");//sprite associated to
//		s.addAttribute("Wind", true);
//	
//		


	}
	
	/**
	 * 
	 * @param type true creates a Dorogovtsev env, false create a grid. 
	 * @param size number of iteration, the greater the bigger maze.
	 * @return a new graph
	 */
	private static Graph generateGraph(boolean type,int size){
		Graph g=new SingleGraph("Random graph");
		Generator gen;
		
		if(type){
			//generate a DorogovtsevMendes environment
			gen= new DorogovtsevMendesGenerator();
			gen.addSink(g);
			gen.begin();
			for(int i=0;i<size;i++){
				gen.nextEvents();
			}
			gen.end();
		}else{
			//generate a square grid environment
			gen= new GridGenerator();
			gen.addSink(g);
			gen.begin();
			for(int i=0;i<size;i++){
				gen.nextEvents();
			}
			gen.end();
			
		}
		return g;
	}
	

	

}
