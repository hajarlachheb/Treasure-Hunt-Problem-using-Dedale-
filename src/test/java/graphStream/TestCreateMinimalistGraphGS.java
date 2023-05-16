package graphStream;
import org.graphstream.graph.*;
import org.graphstream.graph.implementations.*;
import org.graphstream.ui.fx_viewer.FxViewer;
import org.graphstream.ui.view.Viewer;


/**
 * This class presents the minimal requirements to create and display your own graph
 * If you want to add color or form to your node, see the other example : TestCreateGraphGS.java
 * 
 * This example is adapted to the javaFX 2.0 release from the GS official example : 
 * 
 * https://graphstream-project.org/doc/Tutorials/Getting-Started/
 * 
 * @author hc
 *
 */
public class TestCreateMinimalistGraphGS {
	public static void main(String args[]) {
		
		/***********************
		 * Chose your renderer
		 ************************/
		//1) If you wanna use Swing
		//System.setProperty("org.graphstream.ui", "swing"); 
		
		//2) If you want to use javaFx (prefered solution)
		System.setProperty("org.graphstream.ui", "javafx");
		System.setProperty("org.graphstream.debug","true");//required on some systems otherwise it generate a bug. See FxVewer.class line 200 if you wanna understand it
		
		/************************
		 * Create the graph
		 *************************/
		
		Graph graph = new SingleGraph("Tutorial 1");

		graph.addNode("A");
		graph.addNode("B");
		graph.addNode("C");
		graph.addEdge("AB", "A", "B");
		graph.addEdge("BC", "B", "C");
		graph.addEdge("CA", "C", "A");

		graph.display();
	}
}
