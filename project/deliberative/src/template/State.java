package template;

// Import table
import logist.plan.Action;
import logist.simulation.Vehicle;
import logist.task.TaskSet;
import logist.topology.Topology.City;

public class State {

    public City currentCity;
    public TaskSet notDeliveredTask;
    public TaskSet deliveringTask;
    public State parent;
    public Action actionFromParent;
    public double cost;
    public int capacity;
    public int costPerKm;
    private String key;

    // Constructor for initial state
    public State(Vehicle vehicle, TaskSet notDeliveredTask) {

        this.deliveringTask = vehicle.getCurrentTasks();
        //System.out.println("Initially the delivering task is");
        // System.out.println(this.deliveringTask.toString());
        this.notDeliveredTask = notDeliveredTask.clone();
        this.notDeliveredTask.addAll(deliveringTask.clone());
        this.currentCity = vehicle.getCurrentCity();
        this.parent = null;
        this.actionFromParent = null;
        this.cost = 0;
        this.capacity = vehicle.capacity();
        this.costPerKm = vehicle.costPerKm();
        this.key = currentCity.name + notDeliveredTask.toString() + deliveringTask.toString();
    }

    // Constructor for intermediate state
    public State(City currentCity, TaskSet notDeliveredTask,
                 TaskSet deliveringTask, State parent, Action actionFromParent,
                 double cost, int capacity, int costPerKm) {

        this.currentCity = currentCity;
        this.notDeliveredTask = notDeliveredTask;
        this.deliveringTask = deliveringTask;
        this.parent = parent;
        this.actionFromParent = actionFromParent;
        this.cost = cost;
        this.capacity = capacity;
        this.costPerKm = costPerKm;
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