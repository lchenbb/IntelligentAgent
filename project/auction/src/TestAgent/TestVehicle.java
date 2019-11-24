package TestAgent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import logist.task.Task;
import logist.topology.Topology.City;
import logist.plan.Plan;

public class TestVehicle {
	private int capacity;
	private City startCity;
	private double costPerKm;
	
	public ArrayList<TestAction> actions;
	private HashMap<Integer, Task> tasks;
	
	public TestVehicle(City homeCity, int capacity, double costPerKm){
		this.startCity = homeCity;
		this.capacity = capacity;
		this.costPerKm = costPerKm;
		
		actions = new ArrayList<TestAction>();
		tasks = new HashMap<Integer, Task>();
	}
	
	public double marginalCost(List<Task> mytasks){
		double cost0 = cost();
		
		int[] i_pickup = new int[mytasks.size()];
		int[] i_deliver = new int[mytasks.size()];
		
		int i = 0;
		for(Task mytask : mytasks){
			TestAction pickup = new TestAction(mytask.pickupCity, mytask.weight,mytask.id, 0);
			i_pickup[i] = addAction(pickup, 0);
		
			TestAction deliver = new TestAction(mytask.deliveryCity, mytask.weight, mytask.id, 1);
			i_deliver[i] = addAction(deliver, i_pickup[i] + 1);
			i++;
		}
		
		double cost = cost();
		
		for(i = mytasks.size() - 1; i >= 0; i--){
			actions.remove(i_deliver[i]);
			actions.remove(i_pickup[i]);
		}
		
		return cost - cost0;
	}
	
	public double marginalCost(Task task){
		double cost0 = cost();
		
		TestAction pickup = new TestAction(task.pickupCity, task.weight, task.id, 0);
		int i_pickup = addAction(pickup, 0);
		
		TestAction deliver = new TestAction(task.deliveryCity, task.weight, task.id, 1);
		int i_deliver = addAction(deliver, i_pickup + 1);
		
		double cost = cost();
		
		actions.remove(i_deliver);
		actions.remove(i_pickup);
		
		return cost - cost0;
	}
	
	public void addTask(Task task){
		TestAction pickup = new TestAction(task.pickupCity, task.weight, task.id, 0);
		int i_pickup = addAction(pickup, 0);
		
		TestAction deliver = new TestAction(task.pickupCity, task.weight, task.id, 1);
		addAction(deliver, i_pickup + 1);
		
		tasks.put(task.id, task);
	}
	
	private int addAction(TestAction action, int i_min0){
		int i_min = i_min0;
		double c_min = Double.MAX_VALUE;
		for(int i = i_min0; i <= actions.size(); i++){
			actions.add(i, action);
			if(isConsistent() && cost() < c_min){
				i_min = i;
				c_min = cost();
			}
			actions.remove(i);
		}
		actions.add(i_min, action);
		
		return i_min;
	}
	
	private boolean isConsistent(){
		int c = 0;
		for(TestAction a: actions){
			c += a.weight;
			if(c > capacity) return false;
		}
		return true;
	}
	
	public double cost(){
		double cost = 0;
		City c = startCity;
		for(TestAction a: actions){
			cost += c.distanceTo(a.city) * costPerKm;
			c = a.city;
		}
		return cost;
	}
	
	public Plan getPlan(){
		City current = startCity;
		Plan plan = new Plan(current);
		
		for(TestAction a: actions){
			for (City city : current.pathTo(a.city))
				plan.appendMove(city);
			
			if(a.actionType == 0) 
			{plan.appendPickup(tasks.get(a.id));
			
			}
			else 
				{plan.appendDelivery(tasks.get(a.id));
				
				}
			
			current = a.city;
		}
		
		return plan;
	}
}
