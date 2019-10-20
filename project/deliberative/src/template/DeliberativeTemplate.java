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
import java.util.PriorityQueue;
import java.util.Comparator;

/**
 * An optimal planner for one vehicle.
 */
@SuppressWarnings("unused")
public class DeliberativeTemplate implements DeliberativeBehavior {

	enum Algorithm { BFS, ASTAR }
	
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
		String algorithmName = agent.readProperty("algorithm", String.class, "BFS");
		
		// Throws IllegalArgumentException if algorithm is unknown
		algorithm = Algorithm.valueOf(algorithmName.toUpperCase());
		
		// ...
	}
	
	@Override
	public Plan plan(Vehicle vehicle, TaskSet tasks) {
		Plan plan;

		// Compute the plan with the selected algorithm.
		switch (algorithm) {
		case ASTAR:
			// ...
			plan = naivePlan(vehicle, tasks);
			break;
		case BFS:
			// ...
			plan = BFSPlan(vehicle, tasks);
			break;
		default:
			throw new AssertionError("Should not happen.");
		}		
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

	private class State {

		public Vehicle vehicle;
		public City currentCity;
		public TaskSet notDeliveredTask;
		public TaskSet deliveringTask;
		public State parent;
		public Action actionFromParent;
		public double cost;

		// Constructor for initial state
		public State(Vehicle vehicle, TaskSet notDeliveredTask) {

			this.vehicle = vehicle;
			this.deliveringTask = vehicle.getCurrentTasks();
			System.out.println("Initially the delivering task is");
			System.out.println(this.deliveringTask.toString());
			this.notDeliveredTask = notDeliveredTask.clone();
			this.currentCity = vehicle.getCurrentCity();
			this.parent = null;
			this.actionFromParent = null;
			this.cost = 0;
		}

		// Constructor for intermediate state
		public State(Vehicle vehicle, City currentCity, TaskSet notDeliveredTask,
					 TaskSet deliveringTask, State parent, Action actionFromParent,
					 double cost) {

			this.vehicle = vehicle;
			this.currentCity = currentCity;
			this.notDeliveredTask = notDeliveredTask;
			this.deliveringTask = deliveringTask;
			this.parent = parent;
			this.actionFromParent = actionFromParent;
			this.cost = cost;
		}

		// Get key of state
		public String getKey() {

			return this.currentCity.name +
					this.notDeliveredTask.toString() +
					this.deliveringTask.toString();
		}

		public void Update(State parent, Action actionFromParent, int cost) {

			this.parent = parent;
			this.actionFromParent = actionFromParent;
			this.cost = cost;
		}

		// TODO: Decide whether need to add set&get parent/actionFromParent method
	}

	private boolean UpdateNeighbour(State current, State neighbour, Action action) {

		// Check whether going from current state can reduce
		// cost to neighbour state
		double costFromCurrent = current.cost +
									current.currentCity.distanceTo(neighbour.currentCity) *
											current.vehicle.costPerKm();

		if (costFromCurrent < neighbour.cost) {

			// Update neighbour
			neighbour.cost = costFromCurrent;
			neighbour.parent = current;
			neighbour.actionFromParent = action;
			return true;
		}

		return false;
	}

	/**
	 * Get neighbours of current state,
	 * add new ones to stateMap,
	 * update existing neighbours if current state provide better route,
	 * update queue by adding new guys into it
	 * @param currentState
	 * @param stateMap
	 * @return
	 */
	private void findAndUpdateNeighbours(State currentState,
												Map<String, State> stateMap,
												List<State> remainingStates) {

		// Get neighbour states by going to neighbouring city
		// System.out.println("Checking neighbour states");
		for (City neighbourCity : currentState.currentCity.neighbors()) {

			// Construct key for neighbour state
			String key = neighbourCity.name +
					currentState.notDeliveredTask.toString() +
					currentState.deliveringTask.toString();

			// Get or Create neighbour state, Update if necessary
			State neighbour = stateMap.get(key);
			if (neighbour == null) {

				// Create neighbour if it does not exist
				neighbour = new State(currentState.vehicle,
						neighbourCity,
						currentState.notDeliveredTask,
						currentState.deliveringTask,
						currentState,
						new Action.Move(neighbourCity),
						currentState.cost +
								currentState.vehicle.costPerKm() *
										currentState.currentCity.distanceTo(neighbourCity));

				// Put new neighbours into stateMap and remaining States
				stateMap.put(key, neighbour);
				remainingStates.add(neighbour);
			} else {

				// Check whether going from current state
				// can reduce the cost to existing neighbour state
				boolean updated = UpdateNeighbour(currentState, neighbour,
						new Action.Move(neighbour.currentCity));

				// Put updated neighbour into remainingStates
				if (updated) {
					remainingStates.add(neighbour);
				}
			}
		}

		// Get neighbour states by picking up available task
		int remaining_capacity = currentState.vehicle.capacity() -
				currentState.deliveringTask.weightSum();

		for (Task task : currentState.notDeliveredTask) {

			if (!currentState.deliveringTask.contains(task) &&
					task.pickupCity == currentState.currentCity &&
					task.weight <= remaining_capacity) {

				// Get or Create state
				TaskSet newDeliveringTasks = currentState.deliveringTask.clone();
				newDeliveringTasks.add(task);
				String key = currentState.currentCity.name +
						currentState.notDeliveredTask.toString() +
						newDeliveringTasks.toString();

				// Try to get neighbour from history
				State neighbour = stateMap.get(key);

				if (neighbour == null) {

					// Create neighbour if it does not exist
					neighbour = new State(currentState.vehicle,
							currentState.currentCity,
							currentState.notDeliveredTask,
							newDeliveringTasks,
							currentState,
							new Action.Pickup(task),
							currentState.cost);

					// Push new neighbour into remainingStates and stateMap
					stateMap.put(key, neighbour);
					remainingStates.add(neighbour);
				} else {

					// Check for updating neighbour's cost
					boolean updated = UpdateNeighbour(currentState,
							neighbour,
							new Action.Pickup(task));

					// Put neighbour into remainingStates if successfully update
					if (updated ) {

						remainingStates.add(neighbour);
					}
				}
			}
		}

		// Get neighbouring state by delivering task
		for (Task task : currentState.deliveringTask) {

			if (task.deliveryCity == currentState.currentCity) {

				// Remove delivered task from delivering and not delivered
				TaskSet newDeliveringTask = currentState.deliveringTask.clone();
				TaskSet newNotDeliveredTask = currentState.notDeliveredTask.clone();

				newDeliveringTask.remove(task);
				newNotDeliveredTask.remove(task);

				// Get or Create neighbour state
				String key = currentState.currentCity.name +
						newNotDeliveredTask.toString() +
						newDeliveringTask.toString();

				State neighbour = stateMap.get(key);

				// Add new neighbour
				if (neighbour == null) {

					neighbour = new State(currentState.vehicle,
							currentState.currentCity,
							newNotDeliveredTask,
							newDeliveringTask,
							currentState,
							new Action.Delivery(task),
							currentState.cost);

					// Put new neighbour into stateMap and remaining States
					stateMap.put(key, neighbour);
					remainingStates.add(neighbour);
				} else {

					// Check for updating existing neighbour
					boolean updated = UpdateNeighbour(currentState,
							neighbour,
							new Action.Delivery(task));

					// Put updated neighbour into remaining States
					if (updated)
						remainingStates.add(neighbour);
				}
			}
		}
	}


	/**
	 * Construct optimal plan from cost found by BFS
	 * @param stateMap
	 * @param initState
	 * @return
	 */
	private Plan constructOptPlan(Map<String, State> stateMap, State initState) {

		// The terminating state can be defined as
		// states with city where some task delivers and without notDeliveredTasks
		// Find opt plan ending at those states

		// Find terminal with minimal cost
		State bestTerminal = null;
		double bestCost = Double.POSITIVE_INFINITY;

		for (Task task : initState.notDeliveredTask) {

			// Get terminal city
			City terminalCity = task.deliveryCity;

			// Get terminal state at this city
			String key = terminalCity.name + "[]" + "[]";
			State terminal = stateMap.get(key);

			// Update bestTerminal if necessary
			if (terminal.cost < bestCost) {

				bestTerminal = terminal;
				bestCost = terminal.cost;
			}
		}

		System.out.println("The best cost we found is " + bestCost);

		// Construct plan from init state to this terminal
		List<Action> actions = new ArrayList<>();

		State currentState = bestTerminal;

		// Trace up from best terminal until reach the init state
		while (currentState.parent != null) {

			System.out.printf("Current state %s with cost %f ",
							currentState.getKey(),
							currentState.cost);

			// Add current action
			actions.add(currentState.actionFromParent);

			// Trace up
			currentState = currentState.parent;
		}

		// Construct optimal plan from retrieved action list
		Collections.reverse(actions);
		Plan optPlan = new Plan(initState.currentCity, actions);

		System.out.println("Optimal plan is:");
		System.out.println(optPlan.toString());

		return optPlan;
	}
	private Plan BFSPlan(Vehicle vehicle, TaskSet notDeliveredTask) {

		// 1. Put initial state into queue. Initialize stateMap and queue holding the states.
		// 2. Recurrently pop from the queue,
		// try to update neighbors' cost, add freshly detected and updated neighbors into queue
		// 3. Repeat until queue becomes empty
		// 4. Select best route from all possible terminating state

		System.out.println("Constructing bfs plan");

		// Step 1. Initialize beginning state and holders
		Map<String, State> stateMap = new HashMap();
		List<State> remainingStates = new ArrayList();
		State initState = new State(vehicle, notDeliveredTask);
		stateMap.put(initState.getKey(), initState);
		remainingStates.add(initState);

		// Step 2. Conduct BFS
		int count = 0;
		while (remainingStates.size() != 0) {

			// Output count
			count += 1;
			System.out.printf("Round%d\n", count);

			// Pop first state in queue
			State currentState = remainingStates.get(0);
			remainingStates.remove(0);

			// Find and update neighbours'cost
			// Add neighbour to stateMap and remainingStates if necessary
			findAndUpdateNeighbours(currentState, stateMap, remainingStates);
		}

		// Step 4 Construct optimal plan
		Plan optPlan = constructOptPlan(stateMap, initState);

		return optPlan;
	}

	/**
	 * Compute EstCost from current state to terminal state as
	 * max(d(currentCity, task.pickupcity) + d(task.PickupCity, task.DeliverCity)
	 * among all undelivered task
	 *@param state
	 * @return
	 */
	public double heuristic(State state) {

		double h = 0.0;

		// Try some fancy features
		OptionalDouble h_obj =	state.notDeliveredTask
								.stream()
								.mapToDouble(task -> new Double(
								state.currentCity.distanceTo(task.pickupCity) +
								task.pickupCity.distanceTo(task.deliveryCity)))
								.max();

		if (h_obj.isPresent()) {

			h = h_obj.getAsDouble();
		}

		// The following is normal implementation
		/*
		for (Task task : state.notDeliveredTask) {

			double tmp_h = state.currentCity.distanceTo(task.pickupCity) +
					task.pickupCity.distanceTo(task.deliveryCity);

			h = Math.max(h, tmp_h);
		}
		*/

		return h;
	}


	private void findAndUpdateNeighbours(State currentState,
										 Map<String, State> stateMap,
										 Map<String, State> C,
										 PriorityQueue<State> pq) {

	}

	private Plan constructOptPlan(Stateviu\) {

		return null;
	}

	private Plan AStar(Vehicle vehicle, TaskSet notDeliveredTask) {
		// 1. Initialize stateMap, C, PQ
		// 2. Recurrently pop state from PQ,
		// update its neighbours
		// 3. Repeat till find one terminal state
		// 4. Construct optimal plan from the found terminal state

		System.out.println("Executing A* algorithm");

		// Step 1. Initializ
		Map<String, State> stateMap = new HashMap<>();
		Map<String, State> C = new HashMap<>();
		Comparator<State> fcomparator = new Comparator<State>() {
			@Override
			public int compare(State o1, State o2) {
3.5E
				double f1 = heuristic(o1) + o1.cost;
				double f2 = heuristic(o2) + o2.cost;

				if (f1 < f2)
					return -1;
				else if (f1 == f2)
					return 0;
				else
					return 1;
			}
		};
		PriorityQueue<State> pq = new PriorityQueue<>(1, fcomparator);

		// Put init state into stateMap, pq
		State initState = new State(vehicle, notDeliveredTask);
		stateMap.put(initState.getKey(), initState);
		pq.add(initState);

		// Step 2
		while (!pq.isEmpty()) {
                                                                                                                                                                                                                        
			// Pop best element from PQ
			State currentState = pq.poll();

			// Check whether current state is terminal state
			if (currentState.notDeliveredTask.isEmpty()) {
				break;
			}

			// Find and update its' neighbours
			findAndUpdateNeighbours(currentState, stateMap, C, pq);
		}

		// Step 4
		Plan optPlan = constructOptPlan();

		return optPlan;
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

