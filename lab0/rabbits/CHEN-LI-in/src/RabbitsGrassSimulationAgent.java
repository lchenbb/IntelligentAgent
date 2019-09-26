import uchicago.src.sim.gui.Drawable;
import uchicago.src.sim.gui.SimGraphics;
import uchicago.src.sim.space.Object2DTorus;

import java.awt.Color;
import java.util.Random;

/**
 * Class that implements the simulation agent for the rabbits grass simulation.

 * @author CHEN Liangwei, Li Siyuan
 */

public class RabbitsGrassSimulationAgent implements Drawable {

	/* Helper class */
	private enum Direction {

		// Values
		EAST,
		SOUTH,
		WEST,
		NORTH;

		public static Direction getRandomDirection() {

			// Create random instance
			Random random = new Random();

			// Return a random direction
			return Direction.values()[random.nextInt(Direction.values().length)];
		}
	}

	/* Attributes */
	private int x;
	private int y;

	// Initial energy
	private final int INIT_ENERGY = 10;
	private int energy = INIT_ENERGY;

	private static int idCount;
	private int id;

	// Energy cost every day
	private static final int ENERGY_COST = 1;

	// The space it resides in
	private RabbitsGrassSimulationSpace full_space;

	/* Methods */

	public RabbitsGrassSimulationAgent() {

		x = -1;
		y = -1;

		idCount += 1;
		id = idCount;
	}
	public void draw(SimGraphics G) {
		// TODO Auto-generated method stub

		G.drawFastRect(Color.white);
	}

	public int getX() {
		// TODO Auto-generated method stub
		return x;
	}

	public int getY() {
		// TODO Auto-generated method stub
		return y;
	}

	public int getEnergy() {

		return energy;
	}

	public void setEnergy(int value) {

		energy = value;
	}

	public void setXY(int x, int y) {

		this.x = x;
		this.y = y;

		return;
	}

	public int getId() {

		return id;
	}

	public void setFull_space(RabbitsGrassSimulationSpace space) {

		full_space = space;

		return;
	}

	public int eatGrass() {

		// Get grass space
		Object2DTorus grassSpace = full_space.getCurrentGrassSpace();

		// Eat the grass on the land happily
		int grassAmount = (int) grassSpace.getObjectAt(x, y);

		// Remove the grass on the land
		grassSpace.putObjectAt(x, y, new Integer(0));

		if (grassAmount > 0) {

			System.out.println("Agent eating grass !!!");
		}
		return grassAmount;
	}

	// Define attempt to move
	public void tryMove() {

		// Get a random direction
		Direction direction = Direction.getRandomDirection();

		// Decide the new candidate coordinates
		int new_x = 0;
		int new_y = 0;

		switch (direction) {

			case EAST:
				new_x = x + 1;
				new_y = y;
				break;

			case SOUTH:
				new_x = x;
				new_y = y - 1;

			case WEST:
				new_x = x - 1;
				new_y = y;
				break;

			case NORTH:
				new_x = x;
				new_y = y + 1;
				break;
		}

		// Inform the space the candidate new position
		boolean move_result = full_space.moveAgent(x, y, new_x, new_y);

		if (!move_result) {

			System.out.println("Fail to move due to collision");
		}
	}

	public void report() {

		System.out.println("Rabbit " + getId() +
							"at (" + getX() + "," + getY() + ")" +
							" has " + getEnergy() + " energy");
	}

	/**
	 * Define actions in each step
	 */
	public void step() {
		// Try to move
		tryMove();

		// Lose energy due to living
		energy -= ENERGY_COST;

		// Try to eat some grass
		energy += eatGrass();

		// Reproduce, handled in model
		report();
	}
}
