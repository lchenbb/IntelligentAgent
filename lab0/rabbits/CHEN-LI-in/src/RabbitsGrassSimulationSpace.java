/**
 * Class that implements the simulation space of the rabbits grass simulation.
 * @author 
 */

import uchicago.src.sim.space.Object2DTorus;

public class RabbitsGrassSimulationSpace {

    /* Attributes */
    private Object2DTorus grassSpace;
    private Object2DTorus agentSpace;

    /* Methods */
    public RabbitsGrassSimulationSpace(int gridsize) {

        // Initialize space
        grassSpace = new Object2DTorus(gridsize, gridsize);
        agentSpace = new Object2DTorus(gridsize, gridsize);

        // Fill space with zero grass
        for (int i = 0; i < gridsize; i += 1) {

            for (int j = 0; j < gridsize; j += 1) {

                grassSpace.putObjectAt(i, j, new Integer(0));
            }
        }
    }


    /**
     * Methods related to grassSpace
     */

    /**
     * Spread the amount of grass randomly among the space
     * @param amount
     */
    public void spreadGrass(int amount) {

        for (int i = 0; i < amount; i += 1) {

            // Randomly pick a location
            int x = (int)(Math.random() * grassSpace.getSizeX());
            int y = (int)(Math.random() * grassSpace.getSizeY());

            // Get the amount of grass at that location
            int V = getGrassAt(x, y);

            // Set new amount of grass at that location
            grassSpace.putObjectAt(x, y, new Integer(V + 1));
        }
    }

    /**
     * Get amount of grass at pos(x, y)
     * @param x
     * @param y
     * @return
     */
    public int getGrassAt(int x, int y) {

        int V;

        // Get amount of grass at required position
        if (grassSpace.getObjectAt(x, y) != null) {

            V = ((Integer)grassSpace.getObjectAt(x, y)).intValue();
        }
        else {

            V = 0;
        }

        return V;
    }

    /**
     * Get current grassspace object
     * @return
     */
    public Object2DTorus getCurrentGrassSpace() {

        return grassSpace;
    }


    /**
     * Methods related to agentspace
     */

    public boolean isOccupied(int x, int y) {

        if (agentSpace.getObjectAt(x, y) != null)
            return true;

        return false;
    }

    public boolean addAgent(RabbitsGrassSimulationAgent agent) {

        // Set maximal number of trials to add agent
        int MAX_NUM_TRIAL = 10 * agentSpace.getSizeX() * agentSpace.getSizeY();

        // System.out.println("We are adding agent");

        for (int i = 0; i < MAX_NUM_TRIAL; i += 1) {

            // Randomly pick a place
            int x = (int) (Math.random() * agentSpace.getSizeX());
            int y = (int) (Math.random() * agentSpace.getSizeY());

            // Check whether the place is occupied
            boolean occupied = isOccupied(x, y);

            // Add agent if not occupied
            if (!occupied) {

                agentSpace.putObjectAt(x, y, agent);

                // Set agent's position
                agent.setXY(x, y);

                // Set agent's residing space
                agent.setFull_space(this);

                return true;
            }
        }

        // Fail to add agent :(
        return false;
    }

    public Object2DTorus getCurrentAgentSpace() {

        return agentSpace;
    }

    public void removeAgentAt(int x, int y) {

        agentSpace.putObjectAt(x, y, null);
    }

    public RabbitsGrassSimulationAgent getAgentAt(int x, int y) {

        return (RabbitsGrassSimulationAgent) agentSpace.getObjectAt(x, y);
    }
    public boolean moveAgent(int old_x, int old_y, int new_x, int new_y) {


        // Check whether the new pos is occupied
        if (isOccupied(new_x, new_y))
            return false;

        // Get agent for moving
        RabbitsGrassSimulationAgent agent = (RabbitsGrassSimulationAgent) getAgentAt(old_x, old_y);

        // Remove agent from old pos
        removeAgentAt(old_x, old_y);

        // Put it at new position
        agentSpace.putObjectAt(new_x, new_y, agent);

        // Modify agent's pos on agent itself
        agent.setXY(agentSpace.xnorm(new_x), agentSpace.ynorm(new_y));

        return true;
    }
}
