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
			plan = AStar(vehicle, tasks);
			System.out.println("We are doing Astar searching......");
			break;
		case BFS:
			// ...
			plan = BFSPlan(vehicle, tasks);
			System.out.println("We are doing BFS searching");
			break;
		default:
			throw new AssertionError("Should not happen.");
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

	private class State {

		public City currentCity;
		public TaskSet notDeliveredTask;
		public TaskSet deliveringTask;
		public State parent;
		public Action actionFromParent;
		public double cost;
		public int capacity;
		private String key;

		// Constructor for initial state
		public State(Vehicle vehicle, TaskSet notDeliveredTask) {

			this.deliveringTask = vehicle.getCurrentTasks();
			//System.out.println("Initially the delivering task is");
			// System.out.println(this.deliveringTask.toString());
			this.notDeliveredTask = notDeliveredTask.clone();
			this.currentCity = vehicle.getCurrentCity();
			this.parent = null;
			this.actionFromParent = null;
			this.cost = 0;
			this.capacity = vehicle.capacity();

			this.key = currentCity.name + notDeliveredTask.toString() + deliveringTask.toString();
		}

		// Constructor for intermediate state
		public State(City currentCity, TaskSet notDeliveredTask,
					 TaskSet deliveringTask, State parent, Action actionFromParent,
					 double cost, int capacity) {

			this.currentCity = currentCity;
			this.notDeliveredTask = notDeliveredTask;
			this.deliveringTask = deliveringTask;
			this.parent = parent;
			this.actionFromParent = actionFromParent;
			this.cost = cost;
			this.capacity = capacity;

			this.key = currentCity.name + notDeliveredTask.toString() + deliveringTask.toString();
		}

		// Get key of state
		public String getKey() {

			return this.key;
		}

		public void Update(State parent, Action actionFromParent, double cost) {

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
									current.currentCity.distanceTo(neighbour.currentCity);

		if (costFromCurrent < neighbour.cost) {

			// Update neighbour
			neighbour.cost = costFromCurrent;
			neighbour.parent = current;
			neighbour.actionFromParent = action;
			return true;
		}

		return false;
	}


	private void findAndUpdateNeighbours(State currentState,
										 Map<String, State> stateMap,
										 List<State> remainingStates) {

	    // Initialize holder for states to be added
	    List<State> stateList = new ArrayList();

	    // Get neighbours by delivering
        for (Task task : currentState.deliveringTask) {

            // Get delivery city of carrying task
            City deliveredCity = task.deliveryCity;

            TaskSet newDeliveringTask = currentState.deliveringTask.clone();
            TaskSet newNotDeliveredTask = currentState.notDeliveredTask.clone();

            newDeliveringTask.remove(task);
            newNotDeliveredTask.remove(task);

            // Get or create neighbour
            String key = deliveredCity.name + newNotDeliveredTask.toString() + newDeliveringTask.toString();
            State neighbour = stateMap.get(key);

            if (neighbour == null) {

                // Create state if it has not been in stateMap
                neighbour = new State(
                        deliveredCity,
                        newNotDeliveredTask,
                        newDeliveringTask,
                        currentState,
                        new Action.Delivery(task),
                        currentState.cost + currentState.currentCity.distanceTo(deliveredCity),
                        currentState.capacity);

                // Put new state into stateMap and remainingList
                stateMap.put(neighbour.getKey(), neighbour);
                stateList.add(neighbour);
            } else {

                // Put existing neighbour into remainingList iff its cost has been updated by currentState
                boolean updated = UpdateNeighbour(currentState, neighbour, new Action.Delivery(task));

                if (updated) {

                    stateList.add(neighbour);
                }
            }
        }

        // Get neighbour states by picking up available task
        int remaining_capacity = currentState.capacity -
                currentState.deliveringTask.weightSum();

        for (Task task : currentState.notDeliveredTask) {

            if (!currentState.deliveringTask.contains(task) &&
                    task.weight <= remaining_capacity) {

                // Get task pick up
                City taskPickupCity = task.pickupCity;

                // Get or Create state
                TaskSet newDeliveringTasks = currentState.deliveringTask.clone();
                newDeliveringTasks.add(task);

                String key = taskPickupCity.name +
                        currentState.notDeliveredTask.toString() +
                        newDeliveringTasks.toString();

                State neighbour = stateMap.get(key);
                if (neighbour == null) {
                    // Create neighbour if it does not exist
                    neighbour = new State(
                            taskPickupCity,
                            currentState.notDeliveredTask,
                            newDeliveringTasks,
                            currentState,
                            new Action.Pickup(task),
                            currentState.cost + currentState.currentCity.distanceTo(taskPickupCity),
                            currentState.capacity);

                    // Push new neighbour into remainingStates and stateMap
                    stateMap.put(neighbour.getKey(), neighbour);
                    stateList.add(neighbour);
                } else {

                    // Put neighbour into remaining list iff it has been updated by currentState
                    boolean updated = UpdateNeighbour(currentState, neighbour, new Action.Pickup(task));

                    if (updated) {

                        stateList.add(neighbour);
                    }
                }
            }
        }

        // Add updated or new neighbours to remainingStates
        remainingStates.addAll(stateList);
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

			// Add current action
			actions.add(currentState.actionFromParent);

            // Add list of moving actions to reach this state
            List<City> reversedPath = currentState.parent.currentCity.pathTo(currentState.currentCity);
            if (reversedPath.size() > 0) {

                Collections.reverse(reversedPath);
                for (City city : reversedPath) {

                    actions.add(new Action.Move(city));
                }
            }

			// Trace up
			currentState = currentState.parent;
		}

		// Construct optimal plan from retrieved action list
		Collections.reverse(actions);
		Plan optPlan = new Plan(initState.currentCity, actions);

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
			if (count % 1000 == 0) {
			    System.out.printf("%dth round\n", count);
            }
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



	class Heuristic {

		private Map<String, Double> hMap;

		public Heuristic(){

			this.hMap = new HashMap();
		}

		/**
		 * Compute EstCost from current state to terminal state as
		 * max(d(currentCity, task.pickupcity) + d(task.PickupCity, task.DeliverCity)
		 * among all undelivered task
		 *
		 * @param state
		 * @return
		 */
		public double heuristic(State state) {

			// Try to obtain heuristic result from history
			Double result = this.hMap.get(state.getKey());

			if (result != null)
				return result;

			double h = 0.0;

			// Try some fancy features
			/*
			OptionalDouble h_obj =	state.notDeliveredTask
									.stream()
									.mapToDouble(task -> new Double(
									state.currentCity.distanceTo(task.pickupCity) +
									task.pickupCity.distanceTo(task.deliveryCity)))
									.max();

			if (h_obj.isPresent()) {

				h = h_obj.getAsDouble();
			}
			*/

			// The following is normal implementation

			TaskSet notPickUpTasks = state.notDeliveredTask.clone();
			notPickUpTasks.removeAll(state.deliveringTask);

			for (Task task : state.deliveringTask) {

				double tmp_h = state.currentCity.distanceTo(task.deliveryCity);

				h = Math.max(h, tmp_h);
			}

			for (Task task : notPickUpTasks) {

				double tmp_h = state.currentCity.distanceTo(task.pickupCity) +
						task.pickupCity.distanceTo(task.deliveryCity);

				h = Math.max(h, tmp_h);
			}

			// Store computed heuristic into map
			this.hMap.put(state.getKey(), new Double(h));
			return h;
		}
	}


	/**
	 * A* update algorithm
	 * Trivially put all neighbours of currentState into the priority queue
	 * @param currentState
	 * @param stateMap
	 * @param pq
	 */
	private void findAndUpdateNeighbours(State currentState,
										 Map<String, State> stateMap,
										 PriorityQueue<State> pq) {


		// Get neighbouring state by delivering task
		List<State> stateList = new ArrayList<>();

		for (Task task : currentState.deliveringTask) {

			// Get delivery city of carrying task
			City deliveredCity = task.deliveryCity;

			TaskSet newDeliveringTask = currentState.deliveringTask.clone();
			TaskSet newNotDeliveredTask = currentState.notDeliveredTask.clone();

			newDeliveringTask.remove(task);
			newNotDeliveredTask.remove(task);

			// Create neighbour state
			State neighbour = new State(
					deliveredCity,
					newNotDeliveredTask,
					newDeliveringTask,
					currentState,
					new Action.Delivery(task),
					currentState.cost + currentState.currentCity.distanceTo(deliveredCity),
					currentState.capacity);

			stateList.add(neighbour);
		}

		// Get neighbour states by picking up available task
		int remaining_capacity = currentState.capacity -
				currentState.deliveringTask.weightSum();

		for (Task task : currentState.notDeliveredTask) {

			if (!currentState.deliveringTask.contains(task) &&
					task.weight <= remaining_capacity) {

				// Get task pick up
				City taskPickupCity = task.pickupCity;

				// Get or Create state
				TaskSet newDeliveringTasks = currentState.deliveringTask.clone();
				newDeliveringTasks.add(task);

				// Create neighbour if it does not exist
				State neighbour = new State(
						taskPickupCity,
						currentState.notDeliveredTask,
						newDeliveringTasks,
						currentState,
						new Action.Pickup(task),
						currentState.cost + currentState.currentCity.distanceTo(taskPickupCity),
						currentState.capacity);

				// Push new neighbour into remainingStates and stateMap
				stateList.add(neighbour);
			}
		}

		// Add neighbours to priority queue
		pq.addAll(stateList);
	}


	private Plan constructOptPlan(State initState, State terminal) {
		// 1. Initialize action holder
		// 2. Fill actions into holder from terminal to root

		List<Action> actions = new ArrayList();

		State currentState = terminal;

		while (currentState.parent != null) {

			// Add current action: pickup / delivery
			actions.add(currentState.actionFromParent);

			// Add list of moving action to reach this state
			List<City> reversedPath = currentState.parent.currentCity.pathTo(currentState.currentCity);
			if (reversedPath.size() > 0) {

				Collections.reverse(reversedPath);
				for (City city : reversedPath) {

					actions.add(new Action.Move(city));
				}
			}

			currentState = currentState.parent;
		}

		Collections.reverse(actions);

		Plan optPlan = new Plan(initState.currentCity, actions);
		return optPlan;
	}

	private Plan AStar(Vehicle vehicle, TaskSet notDeliveredTask) {
		// 1. Initialize stateMap, C, PQ
		// 2. Recurrently pop state from PQ,
		// update its neighbours
		// 3. Repeat till find one terminal state
		// 4. Construct optimal plan from the found terminal state

		System.out.println("Executing A* algorithm");

		// Step 1. Initialize
		Map<String, State> stateMap = new HashMap<>();
		Map<String, State> C = new HashMap<>();
		Heuristic h = new Heuristic();

		Comparator<State> fcomparator = new Comparator<State>() {
			@Override
			public int compare(State o1, State o2) {

				double f1 = h.heuristic(o1) + o1.cost;
				double f2 = h.heuristic(o2) + o2.cost;

				if (f1 < f2)
					return -1;
				else if (f1 == f2)
					return 0;
				else
					return 1;
			}
		};

		PriorityQueue<State> pq = new PriorityQueue<>(1000, fcomparator);

		// Put init state into stateMap, pq
		State initState = new State(vehicle, notDeliveredTask);
		// stateMap.put(initState.getKey(), initState);
		pq.add(initState);

		// Step 2
		State terminal = null;
		int count = 0;
		long lastTicker = System.currentTimeMillis();
		long currentTicker;
		int avgPQlen = 0;
		long init = System.currentTimeMillis();
		while (!pq.isEmpty()) {

			count += 1;
			avgPQlen += pq.size();
			if (count % 1000 == 0) {
				currentTicker = System.currentTimeMillis();
				System.out.printf("10 rounds take %dms\n", currentTicker - lastTicker);
				System.out.printf("%d round\n", count);
				lastTicker = currentTicker;
			}

			// Pop best element from PQ
			State currentState = pq.poll();
			// Check whether current state is terminal state
			if (currentState.notDeliveredTask.isEmpty()) {
				terminal = currentState;
				break;
			}

			// Trigger update if currentState not in Closed or
			// has lower cost than the copy in Closed

			State oldCurrentState = C.get(currentState.getKey());
			if (oldCurrentState == null || oldCurrentState.cost > currentState.cost) {

				C.put(currentState.getKey(), currentState);

				// Find and update its' neighbours
				findAndUpdateNeighbours(currentState, stateMap, pq);
			}
		}
		long end = System.currentTimeMillis();
		System.out.println("Finish looping takes " + (end - init) + "ms");
		 System.out.println("Planning finished");
		System.out.printf("The avg pq length is %d\n", avgPQlen / count);
		System.out.printf("Number of round is %d\n", count);
		System.out.printf("The best cost we found is %f\n", terminal.cost);
		// Step 4
		Plan optPlan = constructOptPlan(initState, terminal);

		System.out.println("total distance is " + optPlan.totalDistance());
		System.out.println("Initial tasks are" + initState.notDeliveredTask.toString());
		// System.out.printf("Opt plan is %s", optPlan.toString());
		System.out.println(optPlan.toString());
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

