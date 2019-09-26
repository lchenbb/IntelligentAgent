import uchicago.src.sim.engine.Schedule;
import uchicago.src.sim.engine.SimModelImpl;
import uchicago.src.sim.engine.SimInit;
import uchicago.src.sim.gui.Display;
import uchicago.src.sim.gui.DisplaySurface;
import java.awt.Color;
import uchicago.src.sim.gui.ColorMap;
import uchicago.src.sim.gui.Value2DDisplay;
import uchicago.src.sim.gui.Object2DDisplay;
import uchicago.src.sim.engine.BasicAction;
import uchicago.src.sim.util.SimUtilities;
import uchicago.src.sim.analysis.Sequence;
import uchicago.src.sim.analysis.DataSource;
import uchicago.src.sim.analysis.OpenSequenceGraph;
import uchicago.src.reflector.RangePropertyDescriptor;

import java.lang.reflect.Array;
import java.util.ArrayList;

/**
 * Class that implements the simulation model for the rabbits grass
 * simulation.  This is the first class which needs to be setup in
 * order to run Repast simulation. It manages the entire RePast
 * environment and the simulation.
 *
 * @author CHEN Liangwei, Li Siyuan
 */


public class RabbitsGrassSimulationModel extends SimModelImpl {		

	public static void main(String[] args) {

			System.out.println("Hello world");
			System.out.println("Rabbit skeleton");

			SimInit init = new SimInit();
			RabbitsGrassSimulationModel model = new RabbitsGrassSimulationModel();
			// Do "not" modify the following lines of parsing arguments
			if (args.length == 0) // by default, you don't use parameter file nor batch mode 
				init.loadModel(model, "", false);
			else
				init.loadModel(model, args[0], Boolean.parseBoolean(args[1]));
			
		}

		/* Helper class */
		class agentInSpace implements DataSource, Sequence {

			@Override
			public Object execute() {
				return new Double(getSValue());
			}

			@Override
			public double getSValue() {
				return (double) countLivingAgent();
			}
		}

		class grassInSpace implements DataSource, Sequence {

			@Override
			public Object execute() {
				return new Double(getSValue());
			}

			@Override
			public double getSValue() {
				return countGrass();
			}
		}
		/* Attributes */
		private Schedule schedule;

		// DEFAULT VALUES
		private static final int GRIDSIZE = 20;

		private static final int NUMINITRABBITS = 2;

		private static final int NUMINITGRASS = 10;

		private static final int GRASSGROWTHRATE = 50;

		private static final int BIRTHTHRESHOLD = 15;

		private int gridSize = GRIDSIZE;

		private int numInitRabbits = NUMINITRABBITS;

		private int numInitGrass = NUMINITGRASS;

		private int grassGrowthRate = GRASSGROWTHRATE;

		private int birthThreshold = BIRTHTHRESHOLD;

		private int initialEnergy;

		private RabbitsGrassSimulationSpace space;

		private DisplaySurface displaySurf;

		private ArrayList<RabbitsGrassSimulationAgent> agentList;

		private static int STEP = 0;

		private OpenSequenceGraph amountOfObjectInSpace;

		/* Methods */
		public void begin() {
			// TODO Auto-generated method stub

			buildModel();

			buildSchedule();

			buildDisplay();

			// Start display
			displaySurf.display();
			amountOfObjectInSpace.display();
		}

		public void buildModel() {
			// TODO

			// Alert
			System.out.println("Running building model");

			// Initialize the space
			space = new RabbitsGrassSimulationSpace(gridSize);

			// Spread the grass among the space
			space.spreadGrass(numInitGrass);

			// Add agents to the list
			for (int i = 0; i < numInitRabbits; i += 1) {

				addAgent();
			}

			// Ask agents to report
			for (int i = 0; i < agentList.size(); i += 1) {

				// Get agent
				RabbitsGrassSimulationAgent agent = (RabbitsGrassSimulationAgent) agentList.get(i);

				// Let agent report
				agent.report();
			}
		}

		public void buildSchedule() {
			// TODO

			// Define the step class
			class Step extends BasicAction {

				@Override
				public void execute() {

					// Shuffle the agentlist
					SimUtilities.shuffle(agentList);

					System.out.println("Step" + STEP);
					STEP += 1;

					/** 1. Let every agent perform step **/
					for (int i = 0; i < agentList.size(); i += 1) {

						// Get current agent
						RabbitsGrassSimulationAgent agent = (RabbitsGrassSimulationAgent) agentList.get(i);

						// Perform step
						agent.step();
					}

					/** 2. Reap the dead **/
					reapDeadAgent();

					/** 3. Reproduce **/
					reproduce();

					/** 4. Spread the new grown grass **/
					space.spreadGrass(grassGrowthRate);

					/** 5. Update the display **/
					displaySurf.updateDisplay();

					return;
				}
			}

			// Define a trivial count living basic action
			class CountLiving extends BasicAction {

				@Override
				public void execute() {

					int count = countLivingAgent();

					// Test count grass
					int grass_count = countGrass();
					System.out.println("We have " + grass_count + " grass");

					System.out.println("We have " + count + " living agent");
				}
			}

			// Define a update obj count
			class CountObjDisplay extends BasicAction {

				@Override
				public void execute() {

					amountOfObjectInSpace.step();
				}
			}
			// Alert
			System.out.println("Running building Schedule");

			// Add step to schedule
			schedule.scheduleActionBeginning(0, new Step());

			// Add trivial count living
			schedule.scheduleActionAtInterval(10, new CountLiving());

			// Add update count display
			schedule.scheduleActionAtInterval(10, new CountObjDisplay());
		}

		public void buildDisplay() {
	  		// TODO

			// Alert
			System.out.println("Running building Display");

			// Initialize colormap
			ColorMap colormap = new ColorMap();

			// Build a map from grass amount to green extent
			for (int i = 1; i < 16; i += 1) {

				colormap.mapColor(i, new Color(0, (int) i * 8 + 127, 0));
			}
			colormap.mapColor(0, Color.black);

			// Initialize Value2DDisplay object
			Value2DDisplay displayGrass =
					new Value2DDisplay(space.getCurrentGrassSpace(), colormap);

			// Initialize Object2DDisplay object for displaying agents
			Object2DDisplay displayAgent = new Object2DDisplay(space.getCurrentAgentSpace());

			// Add agentList to display objects
			displayAgent.setObjectList(agentList);

			// Bind displayGrass to display surface
			displaySurf.addDisplayable(displayGrass, "Grass");
			displaySurf.addDisplayable(displayAgent, "Agents");

			// Display count
			amountOfObjectInSpace.addSequence("Agent count", new agentInSpace(), Color.RED);
			amountOfObjectInSpace.addSequence("Grass count", new grassInSpace(), Color.GREEN);
	}


		public String[] getInitParam() {
			// TODO Auto-generated method stub
			// Parameters to be set by users via the Repast UI slider bar
			// Do "not" modify the parameters names provided in the skeleton code, you can add more if you want 
			String[] params = { "GridSize", "NumInitRabbits", "NumInitGrass", "GrassGrowthRate", "BirthThreshold"};
			return params;
		}

		public String getName() {
			// TODO Auto-generated method stub

			return "Trivial Repast Model";
			//return null;
		}

		public Schedule getSchedule() {
			// TODO Auto-generated method stub

			return schedule;
		}

		public void setup() {
			// TODO Auto-generated method stub

			// Alert
			System.out.println("Setting up");

			// Reset the space
			space = null;

			// Reset the agent list
			agentList = new ArrayList<>();

			// Reset the schedule
			schedule = new Schedule(1);

			// Reset the surface
			if (displaySurf != null) {

				displaySurf.dispose();
			}

			displaySurf = null;

			if (amountOfObjectInSpace != null) {

				amountOfObjectInSpace.dispose();
			}

			amountOfObjectInSpace = null;

			// Create Display
			displaySurf = new DisplaySurface(this, "Trivial Display");
			amountOfObjectInSpace = new OpenSequenceGraph("Amount of Object in Space", this);

			// Register Display
			registerDisplaySurface("Trivial Display", displaySurf);
			this.registerMediaProducer("Plot", amountOfObjectInSpace);

			// Build sliders
			for (String parameter : this.getInitParam())
				registerSlider(parameter);

		}

		/** Add new agent to agentList
		 *
		 */
		private void addAgent() {

			// Create agent
			RabbitsGrassSimulationAgent a = new RabbitsGrassSimulationAgent();

			// Add agent to list
			agentList.add(a);

			// Add agent to space
			space.addAgent(a);
		}

		public int countLivingAgent() {

			int count = 0;

			for (int i = 0; i < agentList.size(); i += 1) {

				// Get agent
				RabbitsGrassSimulationAgent agent =
						(RabbitsGrassSimulationAgent) agentList.get(i);

				// Increment count if agent is alive
				if (agent.getEnergy() > 0)
					count += 1;
			}

			return count;
		}

		public int countGrass() {

			int count = 0;

			for (int i = 0; i < gridSize; i += 1) {

				for (int j = 0; j < gridSize; j += 1) {


					count += (int) space.getGrassAt(i, j);
				}
			}

			return count;
		}

		private void registerSlider(String parameter) {
			RangePropertyDescriptor slider = new RangePropertyDescriptor(parameter,
					0, 1000, 200);
			descriptors.put(parameter, slider);
		}
		/**
		 * Remove all the dead agents
		 * @return Count of dead agents
		 */
		public int reapDeadAgent() {

			int deadCount = 0;

			for (int i = agentList.size() - 1; i >= 0; i -= 1) {

				// Get the last agent in the list
				RabbitsGrassSimulationAgent agent = (RabbitsGrassSimulationAgent) agentList.get(i);

				// Remove the agent if it has no energy
				if (agent.getEnergy() <= 0) {

					// Remove it from the space
					space.removeAgentAt(agent.getX(), agent.getY());

					// Remove it from the list
					agentList.remove(i);

					// Increment dead count
					deadCount += 1;
				}
 			}

			if (deadCount > 0)
				System.out.println("Killing agent");

			return deadCount;
		}


		public void reproduce() {

			ArrayList<RabbitsGrassSimulationAgent> childrenList
					= new ArrayList<>();

			// Loop through all agent to see whether need to reproduce
			for (int i = 0; i < agentList.size(); i += 1) {

				// Get agent
				RabbitsGrassSimulationAgent agent
						= (RabbitsGrassSimulationAgent) agentList.get(i);

				if (agent.getEnergy() > birthThreshold) {

					// Create child
					RabbitsGrassSimulationAgent child = new RabbitsGrassSimulationAgent();

					// Reduce energy from parent
					agent.setEnergy(agent.getEnergy() - birthThreshold);

					// Child dies if the world is full
					if (agentList.size() + childrenList.size() + 1 > gridSize * gridSize)
						continue;

					System.out.println("Reproducing");

					// Add child to child list
					childrenList.add(child);

					// Put it into space otherwise
					space.addAgent(child);
				}
			}

			// Add children to agentList
			agentList.addAll(childrenList);

			// Free children list
			childrenList = null;
		}
		// "GridSize", "NumInitRabbits", "NumInitGrass", "GrassGrowthRate", "BirthThreshold"
		public void setGridSize(int gs) {

			gridSize = gs;
		}

		public int getGridSize() {

			return gridSize;
		}

		public void setNumInitRabbits(int nir) {

			numInitRabbits = nir;
		}

		public int getNumInitRabbits() {

			return numInitRabbits;
		}

		public void setNumInitGrass(int nig) {

			numInitGrass = nig;
		}

		public int getNumInitGrass(){

			return numInitGrass;
		}

		public void setGrassGrowthRate(int gr) {

			grassGrowthRate = gr;
		}

		public int getGrassGrowthRate() {

			return grassGrowthRate;
		}

		public void setBirthThreshold(int bt) {

			birthThreshold = bt;
		}

		public int getBirthThreshold() {

			return birthThreshold;
		}

}
