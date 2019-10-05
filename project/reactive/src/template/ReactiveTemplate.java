package template;

import java.text.CharacterIterator;
import java.util.Iterator;
import java.util.Random;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collectors;

import com.sun.corba.se.pept.transport.ResponseWaitingRoom;
import logist.simulation.Vehicle;
import logist.agent.Agent;
import logist.behavior.ReactiveBehavior;
import logist.plan.Action;
import logist.plan.Action.Move;
import logist.plan.Action.Pickup;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.topology.Topology;
import logist.topology.Topology.City;
import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;

public class ReactiveTemplate implements ReactiveBehavior {


	private class Pair<T1, T2> {

		/* Attributes */
		public T1 left;
		public T2 right;

		/* Methods */
		public Pair(T1 x, T2 y) {

			this.left = x;
			this.right = y;
		}
	}

	private class State {

		/* Attributes */
		public City src;
		public City target;

		/* Methods */
		public State(City src, City target) {

			this.src = src;
			this.target = target;
		}
	}

	private class State_Action {

		/* Attributes */
		public State state;
		public City action;

		/* Methods */
		public State_Action(State state, City action) {

			this.state = state;
			this.action = action;
		}
	}

	private class State_Action_State {

		/* Attributes */
		public State current_state;
		public City action;
		public State next_state;

		/* Methods */
		public State_Action_State(State current_state, City action,
								  State next_state) {

			this.current_state = current_state;
			this.action = action;
			this.next_state = next_state;
		}
	}

	// Define unit cost for traveling
	public static int UNIT_COST = 3;

	private Random random;
	private double pPickup;
	private int numActions;
	private Agent myAgent;
	private Double discount;
	private double LOOP_TER_THRES = 0.01;
	private Map<State, City> PI;

	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {

		// Reads the discount factor from the agents.xml file.
		// If the property is not present it defaults to 0.95
		Double discount = agent.readProperty("discount-factor", Double.class,
				0.95);

		this.random = new Random();
		this.pPickup = discount;
		this.numActions = 0;
		this.myAgent = agent;
		this.discount = discount;
		this.PI = getOptPlan(topology, td, agent);
		// Get optimal strategy
	}

	/*
	@Override
	public Action act(Vehicle vehicle, Task availableTask) {
		Action action;

		if (availableTask == null || random.nextDouble() > pPickup) {
			City currentCity = vehicle.getCurrentCity();
			action = new Move(currentCity.randomNeighbor(random));
		} else {
			action = new Pickup(availableTask);
		}
		
		if (numActions >= 1) {
			System.out.println("The total profit after "+numActions+" actions is "+myAgent.getTotalProfit()+" (average profit: "+(myAgent.getTotalProfit() / (double)numActions)+")");
		}
		numActions++;
		
		return action;
	}
	*/


	@Override
	public Action act(Vehicle vehicle, Task availableTask) {

		Action action;

		// Get delivery city
		City target = (availableTask != null) ? availableTask.deliveryCity : null;

		// Get current state
		State current = null;

		for (State state : this.PI.keySet()) {

			if (vehicle.getCurrentCity().name == state.src.name && (
					(target != null && state.target != null && target.id == state.target.id) ||
					(target == null && state.target == null)))
				current = state;

		}

		// Get optimal strategy of current state

		City decision = this.PI.get(current);
		// Conduct action
		if (decision != null) {

			// Move to decided city
			action = new Move(decision);
		} else {

			// Pick up task
			action = new Pickup(availableTask);
		}

		// Report
		if (numActions >= 1) {
			System.out.println("The total profit after "+numActions+" actions is "+myAgent.getTotalProfit()+" (average profit: "+(myAgent.getTotalProfit() / (double)numActions)+")");
		}

		numActions++;

		return action;
	}


	/**
	 * Obtain optimal strategy by reinforcement learning
	 * @param topo topology of the underlying graph
	 * @param td task distribution
	 * @return
	 */
	private Map<State, City> getOptPlan(Topology topo, TaskDistribution td, Agent agent) {

		/** Construct states **/
		ArrayList<State> S = new ArrayList();

		for (Iterator<City> iter = topo.iterator(); iter.hasNext(); ) {

			// Get current city
			City current = iter.next();

			for (Iterator<City> iter_tar = topo.iterator(); iter_tar.hasNext(); ) {

				// Get target city
				City target = iter_tar.next();

				// Fill State with (src, target) task
				S.add(new State(current, target));
			}

			// Add (src, no task) to S
			S.add(new State(current, null));
		}

		// Construct Actions HashMap (state: list of actions)
		Map<State, List<City>> A = new HashMap();

		for (Iterator<State> iter = S.iterator(); iter.hasNext(); ) {

			// Get current state
			State state = iter.next();

			// Add entry in Action map for current state
			A.put(state, new ArrayList<City>());

			// Add possible actions (go to neighbour) for current state
			for (City city : state.src.neighbors()) {

				A.get(state).add(city);
			}

			// Add don't go to neighbour (pick up) option if target is not null
			if (state.target != null) {

				A.get(state).add(null);
			}
		}

		// Construct Reward HashMap
		Map<State_Action, Double> R = new HashMap();

		for (Iterator<State> iter = S.iterator(); iter.hasNext(); ) {

			// Get current state
			State state = iter.next();

			// Get available action for current state
			List<City> actions = A.get(state);

			// Set reward for each State_Action pair
			for (City action : actions) {

				State_Action state_act = new State_Action(state, action);

				// Handle picking up case
				if (action == null) {

					// Calculate net reward by reducing traffic cost from task reward
					double reward = td.reward(state.src, state.target) -
										state.src.distanceTo(state.target) * (double) agent.vehicles().get(0).costPerKm();

					R.put(state_act, new Double(reward));
				}

				// Handle moving to neighbour case
				else {

					// Calculate reward as traffic cost
					double reward = -1.0 * state.src.distanceTo(action) * (double) agent.vehicles().get(0).costPerKm();

					R.put(state_act, new Double(reward));
				}
			}
		}

		/* Calculate Transition HashMap<(State, Action, State), Double> */

		// Obtain available resulting states for each state-action pair
		// Transition_Choices
		Map<State_Action, List<State>> TC = new HashMap<State_Action, List<State>>();

		for (State_Action state_act : R.keySet()) {

			// Handle picking up case
			if (state_act.action == null) {

				// Get next city
				City next = state_act.state.target;

				// Set list of possible states at next city
				// as the value corresponding to current state_act
				/*
				TC.put(state_act, S.stream().filter(s -> s.src == next)
											.collect(Collectors.toList()));
				*/
				List<State> next_state = new ArrayList<>();

				for (State s : S) {

					if (s.src == next) {

						next_state.add(s);
					}
				}

				TC.put(state_act, next_state);
			}

			// Handle moving to neighbour case
			else {

				// Get next city
				City next = state_act.action;

				// Add list of possible states as value
				List<State> next_state = new ArrayList<>();

				for (State s : S) {

					if (s.src == next) {

						next_state.add(s);
					}
				}

				TC.put(state_act, next_state);
			}
		}

		// Set transition probability T, i.e., generating probability of
		// task at next city
		// Map current_state, action -> list of possible next (state, proba)
		Map<State_Action, List<Pair<State, Double>>> T = new HashMap();

		for (State_Action state_act : R.keySet()) {

			// Initialize list to hold next (state, proba)
			List<Pair<State, Double>> next_state_proba_list = new ArrayList();

			for (State next_state : TC.get(state_act)) {

				// Add this next_state, proba to list
				next_state_proba_list.add(new Pair(next_state,
											new Double(td.probability(next_state.src, next_state.target))));
			}

			// Put (current_state, action) -> list(next_state, proba) into Transition map
			T.put(state_act, next_state_proba_list);

		}

		/** Conduct value iteration using S, A, R, T, gamma to find the
		 * optimal strategy
		 *
		 * 1. Initialize V randomly
		 * 2. Compute Q s.t. Q(s, a) = R(s, a) + gamma * \sum_{s2 : S}{T(s, a, s2)*V(s2)}
		 * 3. Update V s.t. V(s) = max_{a : A}(Q(s, a))
		 * 4. Go to 2 and repeat till convergence
		 */

		// Initialize V
		Map<State, Double> V = new HashMap();
		for (State state : S) {

			V.put(state, new Double(random.nextDouble()));
		}

		// Initialize Q
		Map<State_Action, Double> Q = new HashMap();

		// Set up looping flag and old value container
		double diff = 0.0;


		// Loop to obtain V and optimal strategy
		while (true) {

			// Reset max difference
			diff = 0.0;

			/** Update Q **/
			for (State_Action state_act : T.keySet()) {

				Double value = new Double(0);

				// Add immediate reward to value
				value += R.get(state_act);

				// Get list of (next_state, proba)
				List<Pair<State, Double>> next_state_proba_list = T.get(state_act);

				// Add future discounted reward to value
				for (Pair<State, Double> pair : next_state_proba_list) {

					// Get one candidate next state and its proba
					State next = pair.left;
					Double proba = pair.right;

					// Increment value by its discounted value
					value += this.discount * proba * V.get(next);
				}

				// Update Q(state_act)
				Q.put(state_act, value);
			}

			/** Update V **/
			for (State state : V.keySet()) {

				// Initialize max_gain to be inf
				Double max_gain = new Double(Double.MIN_VALUE);

				// Update max_gain by checking all possible state_action at current state
				for (State_Action state_act : Q.keySet()) {

					// Check only if state_act happens at current state
					if (state_act.state == state) {

						// Update max_gain
						max_gain = new Double(Math.max(Q.get(state_act), max_gain));
					}
				}

				// Update diff
				diff = Math.max(diff, Math.abs(V.get(state) - max_gain));

				// Update V(s)
				V.put(state, max_gain);
			}

			// Check for terminating
			if (diff < LOOP_TER_THRES) {

				break;
			}
		}

		// Construct Optimal strategy PI
		Map<State, City> PI = new HashMap();

		// Obtain optimal strategy at each state
		for (State state : S) {

			// Initialize best action and max value at current state
			double max_value = -Double.MAX_VALUE;
			City best_action = null;

			// Update best action and max_gain by checking all possible state_act
			// origins from current state
			for (State_Action state_act : Q.keySet()) {

				if (state_act.state == state && Q.get(state_act) > max_value) {

					// Update best action and max value
					max_value = Q.get(state_act);
					best_action = state_act.action;
				}
			}

			// Set optimal strategy for current state
			PI.put(state, best_action);
		}

		// Displaying Value table
		System.out.println("Displaying Value table");
		System.out.printf("Source %-30s Dst\n", "");
		for (State state : V.keySet()) {

			System.out.printf("State: %-30s to %-20s\t\t Value: %.2f\n", state.src, state.target, V.get(state));
		}


		// Display PI
		System.out.println("\nDisplaying Optimal Strategy");
		System.out.printf("Source %-30s Dst\n", "");
		for (State state : PI.keySet()) {

			System.out.printf("State: %-30s to %-20s\t\t Action: %s\n", state.src.name,
								state.target != null ? state.target.name : "no task",
								PI.get(state) != null ?"move to " + PI.get(state).name : "pick up");

		}

		// Check whether not picking up is optimal for some case
		for (State state : PI.keySet()) {

			if (state.target != null && PI.get(state) != null) {

				System.out.println("Exists not picking up as optimal");
				System.out.printf("State: %-30s to %-20s\t\t Action: %s\n", state.src.name,
						state.target != null ? state.target.name : "no task",
						PI.get(state) != null ? "move to " + PI.get(state).name : "pick up");
			}
		}
		return PI;
	}

}
