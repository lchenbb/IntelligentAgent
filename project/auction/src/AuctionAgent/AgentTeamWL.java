package AuctionAgent;

//the list of imports
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import logist.LogistSettings;
import logist.agent.Agent;
import logist.behavior.AuctionBehavior;
import logist.config.Parsers;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;
import AuctionAgent.Action.Type;

@SuppressWarnings("unused")
public class AgentTeamWL implements AuctionBehavior {

	private Topology topology;
	private TaskDistribution distribution;
	private Agent agent;

	private PDPlan myPlan;
	private PDPlan oppPlan;

	private double myCost;
	private double myNewCost;
	private double oppCost;
	private double oppNewCost;

	private List<MyVehicle> myVehicles;
	private List<MyVehicle> oppVehicles;

	private List<City> cityList;
	private List<City> myVehicleCities;

	private double greedyBidRatio = 0.5;
	private double greedyStartRound = 4;

	private double oppRatio = 0.9;
	private double myMarginBidRatio = 0.85;

	final static double oppRatioUpper = 0.95;
	final static double oppRatioLower = 0.8;

	final static double myRatioUpper = 0.9;
	final static double myRatioLower = 0.75;

	private double bidOppMin = Double.MAX_VALUE;
	private int round = 0;
	private long allowedTime = 40000L;

	private double bidAboutPositionMin = 0.9;
	private double bidAboutPositionMax = 1.1;
	double[][] propobality;

	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent) {

		this.topology = topology;
		this.distribution = distribution;
		this.agent = agent;

		LogistSettings ls = null;
		try {
			ls = Parsers.parseSettings("config" + File.separator + "settings_auction.xml");
		} catch (Exception exc) {
			System.out.println("There was a problem loading the configuration file.");
		}

		allowedTime = ls.get(LogistSettings.TimeoutKey.PLAN);

		List<Vehicle> vehicles = agent.vehicles();
		myVehicles = new ArrayList<MyVehicle>(vehicles.size());
		oppVehicles = new ArrayList<MyVehicle>(vehicles.size());

		cityList = topology.cities();
		myVehicleCities = new ArrayList<Topology.City>();

		for (Vehicle vehicle : vehicles) {
			MyVehicle myVehicle = new MyVehicle(vehicle);
			myVehicles.add(myVehicle);
			myVehicleCities.add(vehicle.homeCity());
		}

		// Initialize opponent agent, since we are only required to handle 2 agents auction,
		// it is enough to have one opponent to fight
		for (Vehicle vehicle : vehicles) {
			Random random = new Random();
			City randomCity;
			do {
				int randomNum = random.nextInt(cityList.size());
				randomCity = cityList.get(randomNum);
			} while (myVehicleCities.contains(randomCity));

			MyVehicle oppVehicle = new MyVehicle(null, randomCity, vehicle.capacity(), vehicle.costPerKm());
			oppVehicles.add(oppVehicle);
		}

		this.myPlan = new PDPlan(myVehicles);
		this.oppPlan = new PDPlan(oppVehicles);

		propobality = new double[topology.size()][topology.size()];
		initPro();
	}


	/**
	 * This signal informs the agent about the outcome of an auction. winner
	 * is the id of the agent that won the task. The actual bids of all agents is given
	 * as an array bids indexed by agent id. A null offer indicates that the
	 * agent did not participate in the auction.
	 *
	 * @param previous
	 * @param winner
	 * @param bids
	 */
	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {
		double myBid = bids[agent.id()];

		// Loop to find minimum opponent's bid
		double oppBid = Double.MAX_VALUE;
		for (int i = 0; i < bids.length; i += 1) {

			if (i != agent.id())
				oppBid = Math.min(bids[i], oppBid);
		}

		// Set opponents' bid to min bid if it is smaller
		if (oppBid < bidOppMin) {
			bidOppMin = oppBid;
		}

		System.out.printf("Current round is %d\n", round);

		if (winner == agent.id()) {

			// Handle the case of winning the auction
			// Since we have already won the bid, we may increase the bid a little
			// to have higher profit

			myCost = myNewCost;
			myPlan.updatePlan();

			// Update margin bid ratio and opponent ratio
			myMarginBidRatio = Math.min(myRatioLower,  0.8 * myMarginBidRatio + 0.2 * (myMarginBidRatio + 0.5));
			oppRatio = Math.min(oppRatioLower, 0.8 * oppRatio + 0.2 * (oppRatio + 0.5));

		} else {
			// Handle the case of losing the auction the bid, we may decrease the bid
			// to increase our probability to win in the next bid
			// Since we have already lost

			oppCost = oppNewCost;
			oppPlan.updatePlan();

			myMarginBidRatio = Math.max(myRatioUpper, 0.8 * myMarginBidRatio + 0.2 * (myMarginBidRatio - 0.5));
			oppRatio = Math.max(oppRatioUpper, 0.8 * myMarginBidRatio + 0.2 * (myMarginBidRatio - 0.5));
		}

		if (round == 1) {
			// Handle the starting phase

			double costDiff = Double.MAX_VALUE;
			City predictCity = null;

			for (City city : cityList) {
				if (!myVehicleCities.contains(city)) {

					// Calculate the difference between bid and cost to deliver the first task
					double diff = Math.abs((city.distanceTo(previous.pickupCity)
							+ previous.pickupCity.distanceTo(previous.deliveryCity)) * oppVehicles.get(0).getCostPerKm()
							- oppBid);

					if (diff < costDiff) {
						costDiff = diff;
						predictCity = city;
					}
				}
			}

			oppVehicles.get(0).setInitCity(predictCity);
			System.out.println("City: " + predictCity);
		}

		System.out.println(myBid + " VS " + oppBid);
	}

	@Override
	public Long askPrice(Task task) {

		// Do not participate into auction if no enough capacity provided
		if (myPlan.getBiggestVehicle().getCapacity() < task.weight)
			return null;

		// Calculate estimated cost of self and opponent
		myNewCost = myPlan.solveWithNewTask(task).cost();
		oppNewCost = oppPlan.solveWithNewTask(task).cost();
		double myMarginalCost = myNewCost - myCost;
		double oppMarginalCost = oppNewCost - oppCost;

		System.out.printf("Estimated Self marginal cost for current task is %d\n", myMarginalCost);
		System.out.printf("Estimated Opponent marginal cost for current task is %d\n", oppMarginalCost);

		// Set starting point of my bid value to be max of estimated opponent's bid and estimated self's bid
		// calculated by bid ratio
		double myBidValue = Math.max(oppMarginalCost * oppRatio, myMarginalCost * myMarginBidRatio);

		// Handle maximizing self profit by getting close to the opponent's best bid
		// This step serves to maximize profit if a task's deliver does not cost anything
		// Namely we can freeride this task based on previous routing plan
		if (round > 0 && myBidValue < bidOppMin) {
			myBidValue = Math.max(bidOppMin - 1, 1);
		}

		// Greedy start in the beginning rounds
		if (round < greedyStartRound) {
			myBidValue *= greedyBidRatio ;
		}

		// Move to next round
		round++;

		long myFinalBid = (long) Math.floor(myBidValue);

		return myFinalBid;
	}

	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {

		// Output the tasks auctioned by current agent
		System.out.println("Agent " + agent.id() + " has tasks " + tasks);
		System.out.println(tasks.size());

		PDPlan pdplan = new PDPlan(myVehicles);
		pdplan.solveWithTaskSet(tasks);

		List<Plan> plans = new ArrayList<Plan>();
		PDP pdpAlg = new PDP(myVehicles, tasks);
		pdpAlg.SLSAlgorithmWithInitPlan(allowedTime, pdplan.getBestPlan());

		CentralizedPlan selectedPlan = pdplan.getBestPlan().cost() < pdpAlg.getBestPlan().cost() ? pdplan.getBestPlan()
				: pdpAlg.getBestPlan();

		System.out.println(myPlan.getBestPlan().cost() + "VS" + selectedPlan.cost());

		selectedPlan.printPlan();
		for (MyVehicle vehicle : myVehicles) {
			plans.add(makePlan(vehicle, selectedPlan.getVehicleActions().get(vehicle)));
		}

		while (plans.size() < vehicles.size())
			plans.add(Plan.EMPTY);

		return plans;
	}

	private Plan makePlan(MyVehicle vehicle, LinkedList<Action> linkedList) {

		City currentCity = vehicle.getInitCity();
		Plan plan = new Plan(currentCity);

		for (Action action : linkedList) {
			if (action.type == Type.PICKUP) {
				City nextCity = action.currentTask.pickupCity;
				for (City city : currentCity.pathTo(nextCity)) {
					plan.appendMove(city);
				}
				currentCity = nextCity;
				plan.appendPickup(action.currentTask);
			} else {
				City nextCity = action.currentTask.deliveryCity;
				for (City city : currentCity.pathTo(nextCity)) {
					plan.appendMove(city);
				}
				currentCity = nextCity;
				plan.appendDelivery(action.currentTask);
			}
		}

		return plan;
	}

	private void initPro() {

		int max = 0;
		int min = Integer.MAX_VALUE;

		int[][] cityEdge = new int[topology.size()][topology.size()];
		for (City city1 : topology.cities()) {
			for (City city2 : topology.cities()) {
				cityEdge[city1.id][city2.id] = city1.neighbors().size() * city2.neighbors().size();
				if (cityEdge[city1.id][city2.id] > max) {
					max = cityEdge[city1.id][city2.id];
				}
				if (cityEdge[city1.id][city2.id] < min) {
					min = cityEdge[city1.id][city2.id];
				}
			}
		}

		double ratio = (bidAboutPositionMax - bidAboutPositionMin) / (max - min);
		for (City city1 : topology.cities()) {
			for (City city2 : topology.cities()) {
				propobality[city1.id][city2.id] = (cityEdge[city1.id][city2.id] - min) * ratio + bidAboutPositionMin;
				System.out.println(propobality[city1.id][city2.id]);
			}
		}
	}

	private Plan makePlan(MyVehicle vehicle, LinkedList<Action> linkedList, TaskSet tasks) {
		City currentCity = vehicle.getInitCity();
		Plan plan = new Plan(currentCity);
		HashMap<Integer, Task> taskMap = new HashMap<Integer, Task>();
		for (Task task : tasks) {
			taskMap.put(task.id, task);
		}

		for (Action action : linkedList) {
			System.out.println(action.currentTask.id);

			if (action.type == Type.PICKUP) {
				City nextCity = action.currentTask.pickupCity;
				for (City city : currentCity.pathTo(nextCity)) {
					plan.appendMove(city);
				}
				currentCity = nextCity;

				Task selectedTask = taskMap.get(action.currentTask.id);
				plan.appendPickup(selectedTask);

			} else {
				City nextCity = action.currentTask.deliveryCity;
				for (City city : currentCity.pathTo(nextCity)) {
					plan.appendMove(city);
				}
				currentCity = nextCity;

				Task selectedTask = taskMap.get(action.currentTask.id);
				plan.appendDelivery(selectedTask);
			}
		}
		return plan;
	}
}
