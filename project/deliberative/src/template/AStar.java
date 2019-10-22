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


public class AStar {

    // Attributes
    private Vehicle vehicle;
    private TaskSet notDeliveredTask;

    // Methods
    public AStar(Vehicle vehicle, TaskSet notDeliveredTask) {

        this.vehicle = vehicle;
        this.notDeliveredTask = notDeliveredTask;
    }


    /**
     * Find optimal plan using AStar algorithm
     * @param vehicle
     * @param notDeliveredTask
     * @return
     */
    public Plan plan() {
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

                // Close current state
                C.put(currentState.getKey(), currentState);

                // Find and update its' neighbours
                findAndUpdateNeighbours(currentState, stateMap, pq);
            }
        }

        if (terminal == null) {

            return new Plan(initState.currentCity);
        }
        // Log running statistics
        long end = System.currentTimeMillis();
        System.out.println("Finish looping takes " + (end - init) + "ms");
        System.out.println("Planning finished");
        // System.out.printf("The avg pq length is %d\n", avgPQlen / count);
        System.out.printf("Number of round is %d\n", count);
        System.out.printf("The best cost we found is %f\n", terminal.cost);

        // Step 4
        Plan optPlan = constructOptPlan(initState, terminal);

        System.out.println("total distance is " + optPlan.totalDistance());
        // System.out.println("Initial tasks are" + initState.notDeliveredTask.toString());
        // System.out.printf("Opt plan is %s", optPlan.toString());
        // System.out.println(optPlan.toString());
        return optPlan;
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

    private class Heuristic {

        // Attributes
        private Map<String, Double> hMap;

        // Methods
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
}