package template;

import logist.simulation.Vehicle;

public class VehicleVar extends Obj {
    // Attributes
    public Vehicle v;
    // Methods
    public VehicleVar(Vehicle v, Obj next) {

        this.v = v;
        this.type = Type.Vehicle;
        this.next = next;
        this.time = 0;
    }

    public VehicleVar(VehicleVar vVar) {

        this.v = vVar.v;
        this.type = Type.Vehicle;
        this.next = vVar.next;
        this.time = 0;
    }
}
