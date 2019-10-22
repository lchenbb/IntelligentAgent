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

public class BFS {

    // Attributes
    private Vehicle vehicle;
    private TaskSet notDeliveredTask;

    // Methods
    public BFS(Vehicle vehicle, TaskSet notDeliveredTask) {

        this.vehicle = vehicle;
        this.notDeliveredTask = notDeliveredTask;
    }

    public Plan plan() {

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
               // System.out.printf("%dth round\n", count);
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

        System.out.println("Number of round is " + count);

        return optPlan;
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

        if (bestTerminal == null) {
            return new Plan(initState.currentCity);
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

        //System.out.printf("The optimal plan we found is %s\n", optPlan.toString());

        return optPlan;
    }


    /**
     * Find new and update neighbours at currentState
     * Update remainingStates and stateMap if necessary
     * @param currentState
     * @param stateMap
     * @param remainingStates
     */
    private void findAndUpdateNeighbours(State currentState,
                                         Map<String, State> stateMap,
                                         List<State> remainingStates) {
        // 1. Get neighbours by delivering
        // 2. Get neighbours by picking up

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
     * Update neighbour cost if currentState lead to lower cost alternative
     * @param current
     * @param neighbour
     * @param action
     * @return
     */
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

}