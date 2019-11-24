package template;

//the list of imports
import java.util.*;

import logist.LogistSettings;

import java.io.*;

import logist.Measures;
import logist.behavior.AuctionBehavior;
import logist.behavior.CentralizedBehavior;
import logist.agent.Agent;
import logist.config.Parsers;
import logist.plan.Action;
import logist.simulation.Vehicle;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

import java.util.Random;
import java.util.HashSet;
import java.util.Set;

/**
 * A very simple auction agent that assigns all tasks to its first vehicle and
 * handles them sequentially.
 *
 */
@SuppressWarnings("unused")
public class CentralizedTemplate implements CentralizedBehavior {

    private Topology topology;
    private TaskDistribution distribution;
    private Agent agent;
    private long timeout_setup;
    private long timeout_plan;
    
    @Override
    public void setup(Topology topology, TaskDistribution distribution,
            Agent agent) {
        
        // this code is used to get the timeouts
        LogistSettings ls = null;
        try {
            ls = Parsers.parseSettings("config" + File.separator + "settings_default.xml");
        }
        catch (Exception exc) {
            System.out.println("There was a problem loading the configuration file.");
        }
        
        // the setup method cannot last more than timeout_setup milliseconds
        timeout_setup = ls.get(LogistSettings.TimeoutKey.SETUP);
        // the plan method cannot execute more than timeout_plan milliseconds
        timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN);
        
        this.topology = topology;
        this.distribution = distribution;
        this.agent = agent;
    }
    /*
    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
        long time_start = System.currentTimeMillis();
        
//		System.out.println("Agent " + agent.id() + " has tasks " + tasks);
        Plan planVehicle1 = naivePlan(vehicles.get(0), tasks);

        List<Plan> plans = new ArrayList<Plan>();
        plans.add(planVehicle1);
        while (plans.size() < vehicles.size()) {
            plans.add(Plan.EMPTY);
        }
        
        long time_end = System.currentTimeMillis();
        long duration = time_end - time_start;
        System.out.println("The plan was generated in " + duration + " milliseconds.");
        
        return plans;
    }
    */

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
        long time_start = System.currentTimeMillis();
        List<Plan> plans =  centralizedPlan(vehicles, tasks);
        long time_end = System.currentTimeMillis();
        System.out.printf("THe plan was generated in %d ms", time_end - time_start);
        return plans;
    }

    private Plan naivePlan(Vehicle vehicle, TaskSet tasks) {
        City current = vehicle.getCurrentCity();
        Plan plan = new Plan(current);

        for (Task task : tasks) {
            // move: current city => pickup location
            for (City city : current.pathTo(task.pickupCity)) {
                plan.appendMove(city);
            }

            plan.appendPickup(task);

            // move: pickup location => delivery location
            for (City city : task.path()) {
                plan.appendMove(city);
            }

            plan.appendDelivery(task);

            // set current city
            current = task.deliveryCity;
        }
        return plan;
    }

    /**
     * Generate initial variables by assigning all the tasks to max capacity vehicle
     * @return
     */
    public List<Obj> generateInitVariables(List<Vehicle> vehicles, TaskSet tasks) {

        /*
        List<Obj> vars = new ArrayList<>();

        // Get the vehicle with max capacity
        Vehicle biggestV = Collections.max(vehicles, (Vehicle v1, Vehicle v2) -> v1.capacity() - v2.capacity());

        // Handle the case where the max(load) > max(capacity)
        Task maxLoadTask = Collections.max(tasks, (Task t1, Task t2) -> t1.weight - t2.weight);
        int maxLoad = maxLoadTask.weight;
        if (maxLoad > biggestV.capacity()) {

            System.out.println("UNSOLVABLE");
            System.exit(-1);
        }

        // Assign all the tasks randomly to a vehicle which can
        // accomondate it
        List<Obj> cars = new ArrayList<>();
        for (Vehicle v : vehicles) {
            cars.add(new VehicleVar(v, null));
        }
        // Create next for vs
        VehicleVar vehicle = new VehicleVar(biggestV, null);
        Obj last = null;
        Obj current = null;

        for (Iterator<Task> it = tasks.iterator(); it.hasNext(); ) {

            // Get current task
            Task currentTask = it.next();

            // Get a random vehicle which can accommondate it
            // Collections.shuffle(cars, new Random(10));

            for (Obj obj : cars) {
                if (obj.type == Obj.Type.Vehicle && ((VehicleVar) obj).v.capacity() >= currentTask.weight) {

                    int count = 0;
                    // Find the last action of current vehicle
                    last = obj;
                    while (last.next != null) {
                        last = last.next;
                        count += 1;
                    }

                    // Add pick up and delivery to last
                    current = new PickUp(currentTask, null, count + 1, (VehicleVar) obj);
                    vars.add(current);
                    last.next = current;
                    last = last.next;
                    count += 1;
                    current = new Delivery(currentTask, null, count + 1, (VehicleVar) obj);
                    last.next = current;
                    vars.add(current);

                    break;
                }
            }
        }

        // Add all vehicle to vars
        vars.addAll(cars);

        System.out.println(vars.size());
        // Print initial plan
        /*
        for (Obj obj : vars) {
            if (obj.type == Obj.Type.Vehicle) {

                Obj currentObj = obj;
                while (currentObj != null) {
                    System.out.println("OBJ " + currentObj.type.toString() + " TIME " + currentObj.time + "NEXT" + currentObj.next);
                    currentObj = currentObj.next;
                }
            }

            System.out.println();
        }

        return vars;
        */


        List<Obj> vars = new ArrayList<>();

        // Get the vehicle with max capacity
        Vehicle biggestV = Collections.max(vehicles, (Vehicle v1, Vehicle v2) -> v1.capacity() - v2.capacity());

        // Handle the case where the max(load) > max(capacity)
        Task maxLoadTask = Collections.max(tasks, (Task t1, Task t2) -> t1.weight - t2.weight);
        int maxLoad = maxLoadTask.weight;
        if (maxLoad > biggestV.capacity()) {

            System.out.println("UNSOLVABLE");
            System.exit(-1);
        }

        // Assign all the tasks to the biggestV
        VehicleVar vehicle = new VehicleVar(biggestV, null);
        Obj last = vehicle;

        Obj current = null;
        int count = 1;
        for (Iterator<Task> it = tasks.iterator(); it.hasNext(); ) {

            // Get current task
            Task currentTask = it.next();

            // Add pick up to next
            current = new PickUp(currentTask, null, count, vehicle);
            if (last.type == Obj.Type.Vehicle) {
                last.next = current;
            } else {
                last.next = current;
            }
            vars.add(last);
            count += 1;
            last = current;

            // Add delivery to next
            current = new Delivery(currentTask, null, count, vehicle);
            last.next = current;
            count += 1;
            vars.add(last);
            last = current;
        }

        // Handle last delivery
        vars.add(last);

        // Assign null to all other vehicles
        for (Vehicle v : vehicles) {

            if (v.id() != biggestV.id()) {
                vars.add(new VehicleVar(v, null));
            }
        }

        return vars;
    }


    /**
     * Compute the cost of current plan
     * @param vars
     * @return
     */
    public double cost(List<Obj> vars) {

        double cost = 0.0;
        for (Obj current : vars) {

            // Continue if no next exists
            if (current.next == null) {
                continue;
            }

            // Get costPerKm of underlying vehicle
            double costPerKm;
            switch (current.type) {
                case Vehicle:
                    costPerKm = ((VehicleVar) current).v.costPerKm();
                    break;
                case PickUp:
                    costPerKm = ((PickUp) current).vehicle.v.costPerKm();
                    break;
                default:
                    costPerKm = ((Delivery) current).vehicle.v.costPerKm();
            }

            // Get city of current and next object
            Obj next = current.next;
            City currentCity;
            City nextCity;

            switch (current.type) {
                case Vehicle:
                    currentCity = ((VehicleVar) current).v.getCurrentCity();
                    break;
                case PickUp:
                    currentCity = ((PickUp) current).task.pickupCity;
                    break;
                default:
                    currentCity = ((Delivery) current).task.deliveryCity;
            }

            switch (next.type) {
                case Vehicle:
                    nextCity = ((VehicleVar) next).v.getCurrentCity();
                    break;
                case PickUp:
                    nextCity = ((PickUp) next).task.pickupCity;
                    break;
                default:
                    nextCity = ((Delivery) next).task.deliveryCity;
            }

            // Add cost from current city to next city to total cost
            cost += costPerKm * currentCity.distanceTo(nextCity);
        }

        return cost;
    }

    public List<Obj> changeVehicle(List<Obj> vars, int v1, int v2) {
        // 1. Construct a new list of objs
        // 2. Move first task of v1 to v2

        // Step 1
        List<Obj> newVars = copyObjs(vars);

        // Step 2

        /* Handle source vehicle */
        PickUp pickupToMove = null;
        Delivery deliveryToMove = null;
        Obj preDelivery = null;
        VehicleVar sourceV = null;
        VehicleVar targetV = null;

        for (Obj obj : newVars) {
            if (obj.type == Obj.Type.Vehicle && ((VehicleVar) obj).v.id() == v1)
                sourceV = (VehicleVar) obj;
            if (obj.type == Obj.Type.Vehicle && ((VehicleVar) obj).v.id() == v2)
                targetV = (VehicleVar) obj;
        }

        // Find preDelivery obj and decrement time of objs in sourceV
        pickupToMove = (PickUp) sourceV.next;

        Obj current = pickupToMove;
        int decrement = 1;

        // Handle processing actions between pickup and delivery
        while (current != null) {

            // Decrement time by 1 unit
            switch (current.type) {
                case PickUp:
                    current.time -= decrement;
                    break;
                default:
                    current.time -= decrement;
            }

            // Check whether reach deliveryToMove
            if (current.type == Obj.Type.Delivery &&
                    ((Delivery) current).task.id == pickupToMove.task.id) {

                // Record deliveryToMove
                deliveryToMove = (Delivery) current;

                // Point preDelivery's next to deliveryToMove's next
                // This will point pickupToMove to deliveryToMove's next if
                // it is preDelivery
                preDelivery.next = current.next;
                break;
            }

            // Forward
            preDelivery = current;
            current = current.next;
        }

        // Handle processing actions after delivery
        decrement += 1;
        while (current != null) {

            // Decrement time by 2 units
            switch (current.type){
                case PickUp:
                    current.time -= decrement;
                    break;
                default:
                    current.time -= decrement;
            }

            // Forward
            current = current.next;
        }

        // Point sourceV's next to the second picked task

        sourceV.next = pickupToMove.next;

        /* Handle target vehicle */
        // Increment all the obj's time by 2 in target vehicle
        int increment = 2;
        current = targetV.next;
        while (current != null) {
            switch (current.type) {
                case PickUp:
                    current.time += increment;
                    break;
                default:
                    current.time += increment;
            }
            current = current.next;
        }

        // Insert taskToMove into the first place of target vehicle
        deliveryToMove.next = targetV.next;
        pickupToMove.next = deliveryToMove;
        targetV.next = pickupToMove;
        pickupToMove.time = 1;
        deliveryToMove.time = 2;
        pickupToMove.vehicle = targetV;
        deliveryToMove.vehicle = targetV;

        // Print source vehicle's chain
        current = sourceV;

        /*
        if (checkLoad(newVars) == false) {
            System.out.println("First error in change vehicle");
            System.exit(-1);
        }
        */
        return newVars;
    }

    public List<Obj> changeOrder(List<Obj> vars, int vid, int source_time, int target_time) {
        // Step 1: Copy the old list to a new list
        // Step 2: Move the obj at source_time to target_time

        // Step 1
        List<Obj> newVars = copyObjs(vars);

        // Step 2
        Obj preSource = null;
        Obj source = null;
        Obj target = null;
        VehicleVar sourceV = null;

        // Find vehicle of the task
        for (Obj obj : newVars) {

            if (obj.type == Obj.Type.Vehicle && ((VehicleVar) obj).v.id() == vid) {
                sourceV = (VehicleVar) obj;
                break;
            }
        }

        // Find source action
        preSource = (Obj) sourceV;
        source = preSource.next;
        while (source != null) {
            if (source.time == source_time) {
                break;
            } else {
                // Forward
                preSource = source;
                source = source.next;
            }
        }

        // Decrement time from source to target
        target = source.next;
        while (target != null) {

            target.time -= 1;

            if (target.time == target_time - 1) {

                break;
            }
            target = target.next;
        }

        // Point preSource.next to source.next,
        // Point source.next to target.next,
        // Point target.next to source
        preSource.next = source.next;
        source.next = target.next;
        target.next = source;

        // Update source's time
        source.time = target_time;

        // Check load
        /*
        if ((newVars) == false) {

            System.out.println("First error in change order");
            System.exit(-1);
        }
        */
        return newVars;
    }

    // TODO: Check whether need reset a task to the beginning of procedure

    /**
     * Compute the weight at certain pickup action
     * @param vars
     * @param vid
     * @param ptime
     * @return
     */
    public double getWeightAt(List<Obj> vars, int vid, int ptime) {
        // Step 1: Get the vehicle with vid
        // Step 2: Get all ids of pickup before or equals to ptime
        // Step 3: Get all ids of delivery after ptime
        // Step 4: Build task set at ptime by doing intersection of 2 and 3's outcome
        // Step 5: Compute weight at ptime

        /* Step 1 */
        VehicleVar v = null;
        for (Obj obj : vars) {
            if (obj.type == Obj.Type.Vehicle && ((VehicleVar) obj).v.id() == vid) {
                v = (VehicleVar) obj;
                break;
            }
        }

        /* Step 2 */
        Obj current = v;
        Set<Integer> pickupBefore = new HashSet<>();
        while (current.time <= ptime) {

            if (current.type == Obj.Type.PickUp)
                pickupBefore.add(new Integer(((PickUp) current).task.id));
            current = current.next;
        }

        /* Step 3 */
        Set<Integer> deliverAfter = new HashSet<>();
        while (current != null) {

            if (current.type == Obj.Type.Delivery)
                deliverAfter.add(new Integer(((Delivery) current).task.id));
            current = current.next;
        }

        /* Step 4 */
        Set<Integer> intersection = new HashSet<>(pickupBefore);
        intersection.retainAll(deliverAfter);

        /* Step 5 */
        double weights = 0.0;
        // System.out.printf("LENGTH OF INTERSECTION IS %d\n", intersection.size());
        // System.out.printf("TARGET TIME IS %d\n", ptime);
        for (Obj obj : vars) {

            if (obj.type == Obj.Type.Delivery &&
                    intersection.contains(new Integer(((Delivery) obj).task.id))) {
                weights += ((Delivery) obj).task.weight;
            }
        }
        // System.out.printf("Candiate weight is %f\n", weights);
        return weights;
    }

    public int getRandomLoadedVehicle(List<Obj> vars, int minLoad) {

        Random rand = new Random();

        // Get list of ids such that vehicles with those ids have at least minloads
        List<Integer> loadedIds = new ArrayList<>();
        for (Obj obj : vars) {
            if (obj.type == Obj.Type.Vehicle && obj.next != null) {
                Obj current = obj;
                int count = 0;
                while (current != null) {
                    current = current.next;
                    count += 1;
                    if (count >= 2 * minLoad) {
                        loadedIds.add(new Integer(((VehicleVar) obj).v.id()));
                        break;
                    }
                }
            }
        }

        // Get a random id from list of vehicles with loads
        return loadedIds.get(rand.nextInt(loadedIds.size()));
    }

    public List<List<Obj>> getNeighbours(List<Obj> vars) {
        // Step 1: Get neighbours by move task to different vehicle
        // Step 2: Get neighbours by changing relative order of tasks
        // in one vehicle

        List<List<Obj>> neighbours = new ArrayList<>();

        /* Step 1 */
        // Get a random loaded vehicle
        int sourceId = getRandomLoadedVehicle(vars, 1);
        VehicleVar source = null;
        for (Obj obj : vars) {

            if (obj.type == Obj.Type.Vehicle && ((VehicleVar) obj).v.id() == sourceId) {
                source = (VehicleVar) obj;
                break;
            }
        }
        // System.out.println(source.next);
        int load = ((PickUp) source.next).task.weight;

        // Trigger neighbour construction for each other vehicle
        // who can afford the first task of source
        for (Obj target : vars) {

            if (target.type == Obj.Type.Vehicle &&
                    ((VehicleVar) target).v.id() != sourceId) {

                if (load < ((VehicleVar) target).v.capacity()) {
                    // System.out.printf("Getting neighbours by moving %d to %d\n", sourceId, ((VehicleVar) target).v.id());
                    neighbours.add(changeVehicle(vars, sourceId, ((VehicleVar) target).v.id()));

                }
            }
        }

        /* Step 2 */
        // Get a random vehicle with at least 2 tasks
        sourceId = getRandomLoadedVehicle(vars, 2);
        for (Obj obj : vars) {

            if (obj.type == Obj.Type.Vehicle && ((VehicleVar) obj).v.id() == sourceId) {
                source = (VehicleVar) obj;
                break;
            }
        }

        // Loop though all the actions with source vehicle
        // If pickup, it can be moved to any position before the delivery
        // If delivery, it can be moved to any position before overweight
        Obj current = source.next;
        while (current != null) {
            int pickupTime;
            int deliveryTime;
            switch (current.type) {

                case PickUp:
                    pickupTime = ((PickUp) current).time;

                    // Get delivery time
                    deliveryTime = 0;
                    for (Obj obj : vars) {
                        if (obj.type == Obj.Type.Delivery &&
                                ((Delivery) obj).task.id == ((PickUp) current).task.id) {
                            deliveryTime = obj.time;
                            break;
                        }
                    }

                    // Trigger change order operation between current pickup's time and any
                    // time before delivery time
                    for (int targetTime = pickupTime + 1; targetTime < deliveryTime; targetTime += 1) {
                        // System.out.printf("Change order of vehicle %d  PICKUP at %d to %d\n", sourceId, pickupTime,
                        //        targetTime);
                        neighbours.add(changeOrder(vars, sourceId, pickupTime, targetTime));
                    }
                    break;

                case Delivery:
                    deliveryTime = ((Delivery) current).time;
                    int targetTime = deliveryTime + 1;
                    Obj target = current.next;
                    while (target != null) {

                        // Trigger change order operation between delivery time and target time if
                        // target is not pickup
                        if (target.type == Obj.Type.PickUp &&
                                getWeightAt(vars, sourceId, targetTime) + ((Delivery) current).task.weight >
                        source.v.capacity()) {
                            break;
                        }
                        else if (target.type != Obj.Type.PickUp) {
                            // System.out.printf("Change order of vehicle %d DELIVERY at %d to %d\n", sourceId,
                            //        deliveryTime, targetTime);
                            //System.out.println("Change delivery with delivery");
                            neighbours.add(changeOrder(vars, sourceId, deliveryTime, targetTime));
                        }
                        // Trigger change order operation between delivery time and target time if
                        // target is pickup but postponing this delivery after that pick up does not
                        // make the vehicle overweight
                        else {
                            //System.out.printf("Change order of vehicle %d DELIVERY at %d to %d\n", sourceId,
                            //        deliveryTime, targetTime);
                            neighbours.add(changeOrder(vars, sourceId, deliveryTime, targetTime));
                        }

                        target = target.next;
                        targetTime += 1;
                    }
                    break;
                default:
                    System.out.println("A vehicle should have only pickup or delivery as decendents!!!");
                    System.exit(-1);
            }

            // Forward
            current = current.next;
        }

        return neighbours;
    }


    /**
     * WIth probability p, the algorithm chose best neighbour as next step
     * else it stays at current configuration
     * @param neighbours
     * @param current
     * @param p
     * @return
     */
    public List<Obj> stepForward(List<List<Obj>> neighbours, List<Obj> current, double p, List<Obj> currentBest) {
        // Step 1. Get best neighbour
        // Step 2. Decide next configuration
        // Step 3. Save/Update current best

        /* Step 1 */
        Collections.shuffle(neighbours);
        List<Obj> bestNeighbour = neighbours.get(0);
        double bestCost = cost(bestNeighbour);
        for (List<Obj> neighbour : neighbours) {

            if (cost(neighbour) < bestCost) {
                bestCost = cost(neighbour);
                bestNeighbour = neighbour;
            }
        }

        /* Step 2 */
        Random rand = new Random();
        if (rand.nextDouble() > p) {
            return current;
        } else {
            return bestNeighbour;
        }
    }

    public List<Plan> buildPlanFromVars(List<Obj> vars) {
        // Step 1: Build plan for each vehicle
        // Step 2: Add plan to list according to vehicle id

        // Display raw plan
        for (Obj obj : vars) {
            if (obj.type == Obj.Type.Vehicle) {
                Obj current = obj;
                while (current != null) {
                    switch (current.type) {
                        case Vehicle:
                            System.out.printf("Vehicle %d ", ((VehicleVar) current).v.id());
                            break;
                        case PickUp:
                            System.out.printf("Pickup %d ", ((PickUp) current).task.id);
                            break;
                        case Delivery:
                            System.out.printf("Deliver %d", ((Delivery) current).task.id);
                    }
                    current = current.next;
                }
                System.out.println();
            }
        }
        /* Step 1 */
        // Initialize plan holder with length of number of vehicles
        int vCount = 0;
        for (Obj obj : vars) {
            if (obj.type == Obj.Type.Vehicle)
                vCount += 1;
        }
        List<Plan> plans = new ArrayList<>();
        for (int i = 0; i < vCount; i += 1) {
            plans.add(null);
        }
        // Build plan for each vehicle
        for (Obj obj : vars) {
            if (obj.type == Obj.Type.Vehicle) {

                Plan plan = new Plan(((VehicleVar) obj).v.homeCity());
                Obj current = obj;
                City currentCity = null;
                City nextCity = null;

                while (current != null) {
                    // Add pickup or delivery if they are the case
                    switch (current.type) {
                        case PickUp:
                            plan.appendPickup(((PickUp) current).task);
                            break;
                        case Delivery:
                            plan.appendDelivery(((Delivery) current).task);
                            break;
                    }

                    // Get current city and next city
                    currentCity = null;
                    switch (current.type) {
                        case Vehicle:
                            currentCity = ((VehicleVar) current).v.homeCity();
                            break;
                        case PickUp:
                            currentCity = ((PickUp) current).task.pickupCity;
                            break;
                        case Delivery:
                            currentCity = ((Delivery) current).task.deliveryCity;
                    }

                    if (current.next != null) {
                        switch (current.next.type) {
                            case Vehicle:
                                nextCity = ((VehicleVar) current.next).v.homeCity();
                                break;
                            case PickUp:
                                nextCity = ((PickUp) current.next).task.pickupCity;
                                break;
                            case Delivery:
                                nextCity = ((Delivery) current.next).task.deliveryCity;
                        }

                        // Add path from current city to next city to plan
                        for (City city : currentCity.pathTo(nextCity))
                            plan.appendMove(city);
                    }

                    // Forward
                    current = current.next;
                }

                // Add plan of this vehicle to plan list
                plans.set(((VehicleVar) obj).v.id(), plan);
            }
        }

        /* Step 3 */
        if (cost(plans) < cost(currentBest)) {

        }
        return plans;
    }

    public List<Plan> centralizedPlan(List<Vehicle> vehicles, TaskSet tasks) {
        // Step 1. Generate initial plan
        // Step 2. Get neighbours of current plan
        // Step 3. Move to best neighbour or stay
        // Step 4. Go to step 2 if not reaching max loop
        // Step 5. Generate plan

        /* Step 1 */
        List<Obj> vars = generateInitVariables(vehicles, tasks);

        List<Obj> currentBest = new ArrayList<>();
        currentBest = vars;
        /* Step 2, 3, 4 */
        for (int i = 0; i < 5000; i += 1) {
            System.out.printf("ROUND %d\n", i);
            List<List<Obj>> neighbours = getNeighbours(vars);

            vars = stepForward(neighbours, vars, 0.35, currentBest);
        }

        /* Step 5 */
        List<Plan> plans = buildPlanFromVars(vars);

        // Print out cost of plans
        double total_cost = 0;
        for (int i = 0; i < vehicles.size(); i += 1) {

            Plan plan = plans.get(i);
            int costPerKm = vehicles.get(i).costPerKm();
            total_cost += costPerKm * plan.totalDistance();
        }

        System.out.printf("The total cost of plan with p %f is %f", 0.35, total_cost);
        return plans;

    }

    public List<Obj> copyObjs(List<Obj> vars) {

        List<Obj> newVars = new ArrayList<>();

        for (Obj obj : vars) {

            if (obj.type == Obj.Type.Vehicle) {

                Obj current = obj.next;
                VehicleVar v = new VehicleVar(((VehicleVar) obj).v, null);
                newVars.add(v);
                Obj last = v;

                while (current != null) {

                    switch (current.type) {
                        case PickUp:
                            PickUp pickupNext = new PickUp(((PickUp) current).task, null, current.time, v);
                            newVars.add(pickupNext);
                            last.next = pickupNext;
                            last = last.next;
                            break;
                        case Delivery:
                            Delivery deliveryNext = new Delivery(((Delivery) current).task, null, current.time, v);
                            newVars.add(deliveryNext);
                            last.next = deliveryNext;
                            last = last.next;
                            break;
                    }
                    current = current.next;
                }
            }
        }
        return newVars;
    }
    /*
    public boolean checkLoad(List<Obj> vars) {

        for (Obj obj : vars) {

            if (obj.type == Obj.Type.Vehicle) {

                double load = 0;
                Obj tmp = obj.next;

                while (tmp != null) {
                    switch (tmp.type) {
                        case PickUp:
                            load += 3;
                            break;
                        case Delivery:
                            load -= 3;
                    }
                    if (load > 30) {
                        return false;
                    }

                    tmp = tmp.next;
                }
            }
        }
        return true;
    }
    */
}
