package TestAgent;

/*
import java.util.List;

import logist.behavior.AuctionBehavior;
import logist.agent.Agent;
import logist.simulation.Vehicle;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;

public class AuctionBestResponse implements AuctionBehavior {

	private Agent agent;

	private TestPlan myplan;
	private TestPlan opponentplan;

	private final long MIN_BID = 10;
	private final long DELTA_BID = 10;

	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent) {
		this.agent = agent;
		myplan = new TestPlan(agent.vehicles());
		opponentplan = new TestPlan(agent.vehicles());
	}

	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {
		if (winner == agent.id()) {
			myplan.addTask(previous);
		} else {
			opponentplan.addTask(previous);
		}
	}

	@Override
	public Long askPrice(Task task) {
		long cost = (long) myplan.margCostEstim(task);
		long opponentcost = (long) opponentplan.margCostEstim(task);
		return Math.max(Math.max(cost + DELTA_BID, opponentcost), MIN_BID);
	}

	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
		// Get reward
		long reward = 0;
		for (Task task : tasks) {
			reward += task.reward;
		}

		// Get cost
		double cost = myplan.getCost();
		System.out.printf("The profit of agent %d is %f", agent.id(), reward - cost);

		return myplan.getPlans();
	}
}

*/