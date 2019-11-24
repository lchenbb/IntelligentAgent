package TestAgent;

import java.util.ArrayList;
import java.util.List;

import logist.task.Task;
import logist.plan.Plan;
import logist.simulation.Vehicle;

public class TestPlan {
	
	private ArrayList<TestVehicle> vehicles;
	
	public TestPlan(List<Vehicle> vehicles){
		this.vehicles = new ArrayList<TestVehicle>();
		for(int i = 0; i < vehicles.size(); i++){
			Vehicle v = vehicles.get(i);
			this.vehicles.add(i, new TestVehicle(v.homeCity(), v.capacity(), v.costPerKm()));
		}
	}
	
	public void addTask(Task task){
		
		double minmarginalCost = vehicles.get(0).marginalCost(task);
		int i_min = 0, i = 0;
		for(TestVehicle v : vehicles) {
			double mc = v.marginalCost(task);
			if(mc < minmarginalCost) {
				minmarginalCost = mc; 
				i_min = i;
				}
			i++;
		}
		vehicles.get(i_min).addTask(task);
	}
	
	public double margCostEstim(Task task){
		double minmarginalCost = vehicles.get(0).marginalCost(task);
		for(TestVehicle v : vehicles) {
			double mc = v.marginalCost(task);
			minmarginalCost = (mc < minmarginalCost)? mc:minmarginalCost;
		}
		
		return minmarginalCost;
	}
	
	public double margAvgCostEstim(List<Task> tasks){
		double minmarginalcost = vehicles.get(0).marginalCost(tasks);
		for(TestVehicle v : vehicles) {
			double marginalcost = v.marginalCost(tasks);
			if(marginalcost < minmarginalcost) minmarginalcost = marginalcost;
		}
		
		if(tasks.size()/vehicles.size() > 0){
			double mmc = 0;
			for(int i = 0; i < vehicles.size(); i++) {
				mmc += vehicles.get(i).marginalCost(tasks.subList(
						i*tasks.size()/vehicles.size(), (i+1)*tasks.size()/vehicles.size()));
			}
			if(mmc < minmarginalcost) minmarginalcost = mmc;
		}
		
		return minmarginalcost/tasks.size();
	}
	
	
	public List<Plan> getPlans(){
		List<Plan> plans = new ArrayList<Plan>();
		
		for (TestVehicle v : vehicles)
			plans.add(v.getPlan());

		return plans;
	}

	public double getCost() {

		double cost = 0;
		for (TestVehicle v : vehicles) {

			cost += v.cost();
		}

		return cost;
	}
}
