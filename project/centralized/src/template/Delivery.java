package template;

import logist.task.Task;

public class Delivery extends Obj {
    // Attributes
    Task task;
    public VehicleVar vehicle;

    // Constructor
    public Delivery(Task task, Obj next, int time, VehicleVar vehicle) {

        this.task = task;
        this.next = next;
        this.type = Type.Delivery;
        this.time = time;
        this.vehicle = vehicle;
    }

    public Delivery(Delivery delivery) {

        this.task = delivery.task;
        this.next = delivery.next;
        this.type = Type.Delivery;
        this.time = delivery.time;
        this.vehicle = delivery.vehicle;
    }
}
