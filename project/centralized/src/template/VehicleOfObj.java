package template;

import logist.simulation.Vehicle;

public class VehicleOfObj {

    public Obj action;
    public VehicleVar v;

    public VehicleOfObj(Obj action, VehicleVar v) {

        this.action = action;
        this.v = v;
    }

    public VehicleVar getVehicleOfObj(){

        return this.v;
    }

    public void setVehicleOfObj(VehicleVar v) {

        this.v = v;
    }
}
