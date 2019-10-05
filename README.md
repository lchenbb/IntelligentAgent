# IntelligentAgent
EPFL CS 430
Lab and project programmed in java

## Lab0
Build a pick and delivery simulation system on top of RePast.

The space is a 2D Torus containing randomly genrated ```food```.

The agent has ```Movement```, ```Reproduction```, ```Eating``` and ```Dying``` action.

Run the program by 

```java -jar ./hw0/out/artifacts/lastname1_lastname2_in_jar/lastname1-lastname2-in.jar "" false```

 The initial status of the world including ```gridSize```, ```numInitRabbits```, ```numInitGrass```, ```grassGrowthRate``` and ```birthThreshold``` can be set in the GUI generated.
 
 
## Project/Reactive_Agent
This lab builds a reactive agent using value iteration to find an optimal strategy for a pickup-delivery problem, which is a instance of Markov decision process.

The detailed descriptions of the problem can be found at [here](https://github.com/lchenbb/IntelligentAgent/blob/master/project/LogistPlatform.pdf).

To try different settings, please follow the instructions [here](https://github.com/lchenbb/IntelligentAgent/blob/master/project/reactive.pdf) to create or modify the xml files.

For visualing how our agent performs, run
```
java -jar ../logist/logist.jar config/reactive.xml reactive-random
```
inside the reative directory.
## Authors
* **CHEN Liangwei** [email](mailto:liangwei.chen@epfl.ch)
* **LI Siyuan** [email](mailto:siyuan.li@epfl.ch)
 
