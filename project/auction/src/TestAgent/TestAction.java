package TestAgent;

import logist.topology.Topology.City;

public class TestAction {

	//action type: 0 means pickup 1 means delivery
	public int actionType;
	public City city;
	public int weight;
	public int id;
	
	public TestAction(City city, int weight, int id, int actionType){
		this.city = city;
		this.weight = (actionType == 0) ? weight : -weight;
		this.id = id;
		this.actionType = actionType;
	}	
}
