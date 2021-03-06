/*
 * This project partially refers to project developed by Liang Zixuan and Wang Junxiong in
 * https://github.com/OVSS/Intelligent-Agent/tree/master/5%20Auction%20Agent
 *
 * However, we made the changes in design including but not limited to the updating rule of
 * oppRatio and myBidRatio, the decision of bid for "costless" task and the decision from initialBid
 * to finalBid.
 *
 * The advantages we exploited from their projects are namely the implementation of Centralized Plan,
  * design of project structure and intuition of designing the bidding strategy.
 */
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
public class AgentOur implements AuctionBehavior {

	private PDPlan computedPlan;
	private double ourCost;
	private double ourupdatedCost;
	final static double ourUB = 0.85;
	final static double ourLB = 0.75;
	private List<City> cityList;
	private List<MyVehicle> ourVehicles;
	
	private PDPlan oppoentPlan;
	private double oppCost;
	private double oppupdatedCost;
	private List<MyVehicle> oppVehicles;
	final static double oppoentUB = 0.9;
	final static double oppoentLB = 0.8;
	
	private double bidOppMin = Double.MAX_VALUE;
	private int round = 0;
	private long allowedTime = 40000L;
	private double greedyBidRatio = 0.5;
	private double greedyStartRound = 4;
	private double oppoentRatio = 0.9;
	private double mBidRatio = 0.85;
	private double bidAboutPositionMin = 0.9;
	private double bidAboutPositionMax = 1.1;
	private List<City> ourInitCity;
	
	private Topology topology;
	private TaskDistribution distribution;
	private Agent agent;

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
		ourVehicles = new ArrayList<MyVehicle>(vehicles.size());
		oppVehicles = new ArrayList<MyVehicle>(vehicles.size());

		cityList = topology.cities();
		ourInitCity = new ArrayList<Topology.City>();

		for (Vehicle vehicle : vehicles) {
			MyVehicle myVehicle = new MyVehicle(vehicle);
			ourVehicles.add(myVehicle);
			ourInitCity.add(vehicle.homeCity());
		}

		// Initialize opponent agent, since we are only required to handle 2 agents auction,
		// it is enough to have one opponent to fight
		for (Vehicle vehicle : vehicles) {
			Random random = new Random();
			City randomCity;
			do {
				int randomNum = random.nextInt(cityList.size());
				randomCity = cityList.get(randomNum);
			} while (ourInitCity.contains(randomCity));

			MyVehicle oppVehicle = new MyVehicle(null, randomCity, vehicle.capacity(), vehicle.costPerKm());
			oppVehicles.add(oppVehicle);
		}

		this.computedPlan = new PDPlan(ourVehicles);
		this.oppoentPlan = new PDPlan(oppVehicles);
	}


	/**
	 * This function informs the agent about the outcome of previous auction
	 * a winner id and all the bids on this object are provided.
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

			ourCost = ourupdatedCost;
			computedPlan.updatePlan();

			// Update margin bid ratio and opponent ratio

			mBidRatio = Math.max(ourUB, 0.8 * mBidRatio + 0.2 * (mBidRatio - 0.5));
			oppoentRatio = Math.min(oppoentLB, 0.8 * oppoentRatio + 0.2 * (oppoentRatio + 0.5));

		} else {
			// Handle the case of losing the auction the bid, we may decrease the bid
			// to increase our probability to win in the next bid
			// Since we have already lost

			oppCost = oppupdatedCost;
			oppoentPlan.updatePlan();

			mBidRatio = Math.min(ourLB,  0.8 * mBidRatio + 0.2 * (mBidRatio + 0.5));
			oppoentRatio = Math.max(oppoentUB, 0.8 * mBidRatio + 0.2 * (mBidRatio - 0.5));
		}

		if (round == 1) {
			// Handle the starting phase

			double costDiff = Double.MAX_VALUE;
			City predictCity = null;

			for (City city : cityList) {
				if (!ourInitCity.contains(city)) {

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
		}

	}

	/**
	 * This function decides the bid of an auction
	 * @param task
	 * @return
	 */
	@Override
	public Long askPrice(Task task) {

		// Do not participate into auction if no enough capacity provided
		if (computedPlan.getBiggestVehicle().getCapacity() < task.weight)
			return null;

		// Calculate estimated cost of self and opponent
		ourupdatedCost = computedPlan.solveWithNewTask(task).cost();
		oppupdatedCost = oppoentPlan.solveWithNewTask(task).cost();
		double myMarginalCost = ourupdatedCost - ourCost;
		double oppMarginalCost = oppupdatedCost - oppCost;

		System.out.printf("Estimated Self marginal cost for current task is %f\n", myMarginalCost);
		System.out.printf("Estimated Opponent marginal cost for current task is %f\n", oppMarginalCost);

		// Set starting point of my bid value to be max of estimated opponent's bid and estimated self's bid
		// calculated by bid ratio
		double myBidValue = Math.max(oppMarginalCost * oppoentRatio, myMarginalCost * mBidRatio);

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

		PDPlan pdplan = new PDPlan(ourVehicles);
		pdplan.solveWithTaskSet(tasks);

		List<Plan> plans = new ArrayList<Plan>();
		PDP pdpAlg = new PDP(ourVehicles, tasks);
		if (pdplan.getBestPlan() == null) {
			for (int i = 0; i < ourVehicles.size(); i += 1) {
				plans.add(new Plan(ourVehicles.get(i).getInitCity()));
			}

			return plans;
		}
		pdpAlg.SLSAlgorithmWithInitPlan(allowedTime, pdplan.getBestPlan());

		CentralizedPlan selectedPlan = pdplan.getBestPlan().cost() < pdpAlg.getBestPlan().cost() ? pdplan.getBestPlan()
				: pdpAlg.getBestPlan();

		System.out.println(computedPlan.getBestPlan().cost() + "VS" + selectedPlan.cost());

		// Calculate the profit
		long profit = 0;
		for (Task task : tasks) {
			profit += task.reward;
		}
		System.out.printf("The profit of agent %d is %f\n", agent.id(), profit - selectedPlan.cost());

		selectedPlan.printPlan();
		for (MyVehicle vehicle : ourVehicles) {
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
