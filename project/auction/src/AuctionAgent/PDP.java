package AuctionAgent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import logist.task.Task;
import logist.task.TaskSet;
import AuctionAgent.Action.Type;

public class PDP {

	private List<MyVehicle> vehicles;
	private TaskSet tasks;
	private static double prop = 0.35;
	private CentralizedPlan bestPlan;
	private double minCost = Integer.MAX_VALUE;

	public PDP(List<MyVehicle> vehicles, TaskSet tasks) {
		super();
		this.vehicles = vehicles;
		this.tasks = tasks;
	}

	public void SLSAlgorithmWithInitPlan(long maxTime, CentralizedPlan plan) {

		maxTime = (maxTime * 4) / 5;
		long startTime = System.currentTimeMillis();
		bestPlan = plan;
		int maxIter = 1500;

		// Prevent timeout by returning best plan obtained by maxTime * 0.8
		for (int i = 0; i < maxIter; i++) {

			if (System.currentTimeMillis() - startTime > maxTime) {
				return;
			}

			// Update centralized plan by move to neighbour or halt for one round
			CentralizedPlan oldPlan = plan;

			ArrayList<CentralizedPlan> planSet = ChooseNeighbours(oldPlan);
			plan = localChoice(oldPlan, planSet);
		}
	}

	/**
	 * Choose neighbours of current plan
	 * @param oldPlan
	 * @return list of neighbours
	 */
	public ArrayList<CentralizedPlan> ChooseNeighbours(CentralizedPlan oldPlan) {
		ArrayList<CentralizedPlan> planSet = new ArrayList<CentralizedPlan>();

		Random random = new Random();
		int selectVehicleNum = random.nextInt(vehicles.size());
		MyVehicle selectVehicle = vehicles.get(selectVehicleNum);

		// Find neighbour by move a task from one vehicle to the other
		for (MyVehicle targetVehicle : vehicles) {

			for (MyVehicle currentVehicle : vehicles) {

				// Get the actions of the selected vehicle

				if (oldPlan.getVehicleActions() == null) {
					continue;
				}

				LinkedList<Action> vehicleActions = oldPlan.getVehicleActions().get(targetVehicle);

				if (currentVehicle != targetVehicle && vehicleActions.size() > 0) {

					for (int i = 0; i < oldPlan.getVehicleActions().get(targetVehicle).size(); i++) {

						// Get the first action from selected vehicle to exchange
						Action exchangeAction = oldPlan.getVehicleActions().get(targetVehicle).get(0);

						// Put the selected action to current vehicle's first pos if current
						// vehicle has available capacity
						if (exchangeAction.currentTask.weight <= currentVehicle.getCapacity()) {

							List<CentralizedPlan> planList = moveToVehicle(oldPlan, targetVehicle, currentVehicle);

							for (CentralizedPlan plan : planList) {

								if (!plan.violateConstraint()) {
									planSet.add(plan);
								}
							}
						}
					}
				}
			}
		}

		LinkedList<Action> vehicleAction = oldPlan.getVehicleActions().get(selectVehicle);

		// Find neighbour by changing tasks orders in one vehicle
		int length = vehicleAction.size();
		if (length > 2) {
			for (int taskID = 0; taskID < length; taskID++) {

				if (vehicleAction.get(taskID).type == Type.PICKUP) {

					List<CentralizedPlan> planList = changeTaskOrder(oldPlan, selectVehicle, taskID);
					for (CentralizedPlan plan : planList) {
						if (!plan.violateConstraint()) {
							planSet.add(plan);
						}
					}
				}
			}
		}
		return planSet;
	}

	public List<CentralizedPlan> moveToVehicle(CentralizedPlan oldPlan, MyVehicle v1, MyVehicle v2) {
		// Move the first task of v1 to v2

		CentralizedPlan newPlan = null;
		try {
			newPlan = (CentralizedPlan) oldPlan.clone();
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
		}

		Task startTask = newPlan.getVehicleActions().get(v1).get(0).currentTask;

		newPlan.removeTask(startTask);

		Action pickupAction = new Action(Type.PICKUP, startTask);
		Action deliverAction = new Action(Type.DELIVERY, startTask);

		LinkedList<Action> stateListV2 = newPlan.getVehicleActions().get(v2);


		stateListV2.addFirst(pickupAction);

		List<CentralizedPlan> planSet = new ArrayList<CentralizedPlan>();

		for (int i = 1; i <= stateListV2.size(); i++) {

			LinkedList<Action> stateListCopy = (LinkedList<Action>) stateListV2.clone();
			stateListCopy.add(i, deliverAction);

			CentralizedPlan copyPlan;
			try {

				copyPlan = (CentralizedPlan) newPlan.clone();
				copyPlan.getVehicleActions().put(v2, stateListCopy);
				planSet.add(copyPlan);

			} catch (CloneNotSupportedException e) {
				e.printStackTrace();
			}

		}
		return planSet;

	}

	private List<CentralizedPlan> changeTaskOrder(CentralizedPlan oldPlan, MyVehicle v1, int tIdx) {

		List<CentralizedPlan> neighbours = new ArrayList<CentralizedPlan>();

		CentralizedPlan newPlan = null;
		try {
			newPlan = (CentralizedPlan) oldPlan.clone();
		} catch (CloneNotSupportedException e) {

			e.printStackTrace();
		}

		LinkedList<Action> actionList = newPlan.getVehicleActions().get(v1);
		Task task = actionList.get(tIdx).currentTask;
		newPlan.removeTask(task);

		Action pickupAction = new Action(Type.PICKUP, task);
		Action deliveryAction = new Action(Type.DELIVERY, task);

		for (int insertFirst = 0; insertFirst <= actionList.size(); insertFirst++) {

			for (int insert2 = insertFirst + 1; insert2 <= actionList.size() + 1; insert2++) {

				CentralizedPlan copyPlan;
				try {

					copyPlan = (CentralizedPlan) newPlan.clone();
					LinkedList<Action> copyList = (LinkedList<Action>) actionList.clone();
					copyList.add(insertFirst, pickupAction);
					copyList.add(insert2, deliveryAction);
					copyPlan.getVehicleActions().put(v1, copyList);
					neighbours.add(copyPlan);

				} catch (CloneNotSupportedException e) {

					e.printStackTrace();
				}
			}
		}

		return neighbours;
	}

	private CentralizedPlan localChoice(CentralizedPlan oldPlan, ArrayList<CentralizedPlan> neighbours) {

		CentralizedPlan returnPlan = oldPlan;
		CentralizedPlan minCostPlan = null;
		double minCost = Integer.MAX_VALUE;

		// Find the plan with minimum cost
		double currentCost;
		for (CentralizedPlan plan : neighbours) {
			currentCost = plan.cost();
			if (currentCost < minCost) {
				minCostPlan = plan;
				minCost = currentCost;
			}
		}

		if (minCost < this.minCost) {
			this.bestPlan = minCostPlan;
			this.minCost = minCost;
		}

		Random random = new Random();
		double num = random.nextDouble();
		if (num < prop) {
			returnPlan = minCostPlan;
		} else if (num < 2 * prop) {
			returnPlan = oldPlan;
		} else {
			returnPlan = neighbours.get(random.nextInt(neighbours.size()));
		}
		return returnPlan;
	}

	public CentralizedPlan getBestPlan() {
		return bestPlan;
	}

}
