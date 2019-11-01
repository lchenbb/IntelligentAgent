package template;

import logist.task.Task;

public class PickUp extends Obj {
    // Attributes
    public Task task;
    public VehicleVar vehicle;

    // Methods
    public PickUp(Task task, Obj next, int time, VehicleVar vehicle) {

        this.task = task;
        this.time = time;
        this.type = Type.PickUp;
        this.next = next;
        this.vehicle = vehicle;
    }

    public PickUp(PickUp pickup) {

        this.task = pickup.task;
        this.type = Type.PickUp;
        this.next = pickup.next;
        this.vehicle = pickup.vehicle;
    }
}
