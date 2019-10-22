package template;

/* import table */
import logist.plan.Action;
import logist.simulation.Vehicle;
import logist.agent.Agent;
import logist.behavior.DeliberativeBehavior;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;
import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;

import java.io.PrintWriter;
import java.util.*;
import java.util.HashSet;
import java.util.Set;
import java.util.PriorityQueue;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * An optimal planner for one vehicle.
 */
@SuppressWarnings("unused")
public class DeliberativeTemplate implements DeliberativeBehavior {

	enum Algorithm { BFS, ASTAR, TRIVIAL }
	
	/* Environment */
	Topology topology;
	TaskDistribution td;
	
	/* the properties of the agent */
	Agent agent;
	int capacity;

	/* the planning class */
	Algorithm algorithm;
	
	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {
		this.topology = topology;
		this.td = td;
		this.agent = agent;
		
		// initialize the planner
		int capacity = agent.vehicles().get(0).capacity();
		String algorithmName = agent.readProperty("algorithm", String.class, "AStar");
		
		// Throws IllegalArgumentException if algorithm is unknown
		algorithm = Algorithm.valueOf(algorithmName.toUpperCase());
		
		// ...
	}
	
	@Override
	public Plan plan(Vehicle vehicle, TaskSet tasks) {
		Plan plan;

		// Compute the plan with the selected algorithm.
		long init = System.currentTimeMillis();
		switch (algorithm) {
		case ASTAR:
			// ...
			AStar astarPlanner = new AStar(vehicle, tasks);
			plan = astarPlanner.plan();
			System.out.println("We are doing Astar searching......");
			break;
		case BFS:
			// ...
			BFS bfsPlanner = new BFS(vehicle, tasks);
			plan = bfsPlanner.plan();
			System.out.println(agent.id() + "Initial city is " + vehicle.getCurrentCity() + "\n");
			System.out.println("Current undelivered tasks are\n" + tasks.toString());
			System.out.printf("%d's Current Delivering tasks are %s\n\n", agent.id(), vehicle.getCurrentTasks().toString());
			System.out.println(agent.id() + "find optimal plan " + plan.toString() + "\n");
			System.out.println("We are doing BFS searching\n");
			break;
		default:
			plan = naivePlan(vehicle, tasks);
			break;
			//throw new AssertionError("Should not happen.");
		}
		long end = System.currentTimeMillis();

		System.out.printf("The time used for planning is %dms", end - init);
		return plan;
	}
	
	private Plan naivePlan(Vehicle vehicle, TaskSet tasks) {
		City current = vehicle.getCurrentCity();
		Plan plan = new Plan(current);

		for (Task task : tasks) {
			// move: current city => pickup location
			for (City city : current.pathTo(task.pickupCity))
				plan.appendMove(city);

			plan.appendPickup(task);

			// move: pickup location => delivery location
			for (City city : task.path())
				plan.appendMove(city);

			plan.appendDelivery(task);

			// set current city
			current = task.deliveryCity;
		}
		return plan;
	}

	@Override
	public void planCancelled(TaskSet carriedTasks) {
		
		if (!carriedTasks.isEmpty()) {
			// This cannot happen for this simple agent, but typically
			// you will need to consider the carriedTasks when the next
			// plan is computed.

		}
	}
}

