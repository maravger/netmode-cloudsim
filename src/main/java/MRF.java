import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;

public class MRF {

    private static final double A = 1/1000.0;
    private static final double B = 10/1000.0;
    private static final double L = 1;
    private static final double k = 5;
    private static final double C0 = 2;
    private static final int SWEEPS = 20;
    private static final double X0_PERC = 0.5;
    private static final int RELAXATION_FACTOR = 5;
    private static final int MAX_NEIGHBORHOOD_SIZE = 3; // If bigger, problem becomes too computationally intensive!!

    private static int apps = 2;
    private static int hops = 1;
    private static int gridSize = 3;
    private static int[] residualResources = {1, 1, 1, 0, 2, 2, 2, 1, 1};
    private static int[][] residualWorkload = {{5, 10}, {5, 10}, {10, 5}, {10, 5}, {5, 5}, {10, 5}, {10, 10}, {10, 5}, {5, 10}};
    private static double[][] formationsWorkload = {{37.35, 41.35}, {82.24, 37.35}, {74.7, 37.35}, {119.59, 37.35},
        {112.05, 37.35}, {37.35, 82.24}, {82.24, 82.24}, {74.7, 82.24}, {37.35, 74.7}, {82.24, 74.7}, {74.7, 74.7}, {37.35, 119.59}, {37.35, 112.05}};
    private static int[] powerConsumption = {2800, 3000, 4200, 4400, 5600, 3000, 3200, 4400, 4200, 4400, 5600, 4400, 5600};
    private static MRFNode[] nodes;
    private static String timeStamp;

    public static void balanceLoadBetweenPois(int[][] residualWorkload, int[] residualResources) {
        double[] totalVc = new double[nodes.length];

        // Initialize Vcs with a big number
        for (int i = 0; i < totalVc.length; i++)
            totalVc[i] = 100;

//        updateVcCSVs(0, Arrays.stream(totalVc).sum());
        System.out.println("Starting Workload balance: " + "\n");
        for (int i = 0; i < nodes.length; i++) {
            System.out.print(String.format("%8s", Arrays.toString(nodes[i].currentWorkload)));
            if (i != 0 && (i + 1) % gridSize == 0)
                System.out.println();
            else
                System.out.print(" | ");
        }
        for (int sweep = 0; sweep < SWEEPS; sweep ++) {
            System.out.println("------------------------------------------------------------------------\n");
            System.out.println("Sweep: " + sweep + "\n");
            for (MRFNode mainNode: nodes) {
//                System.out.println("Checking Main Node with id: " + mainNode.id);
                ArrayList<int[][]> feasibleNeighborhoodStates = mainNode.calculateFeasibleNeighborhoodWorkloadStates();
                double[] neighborhoodStatesTp = new double[feasibleNeighborhoodStates.size()]; // goes hand in hand with the above array
                double[] neighborhoodStatesVc = new double[feasibleNeighborhoodStates.size()]; // goes hand in hand with the above array
                double[] neighborhoodStatesP = new double[feasibleNeighborhoodStates.size()]; // goes hand in hand with the above array
                double[] neighborhoodStatesCP = new double[feasibleNeighborhoodStates.size()]; // goes hand in hand with the above array

                int i = 0;
                // For every state of Xi (paired with the feasible states of Xj in the neighborhood)
                for (int[][] feasibleNeighborhoodState : feasibleNeighborhoodStates) {
//                    System.out.println("Checking Neighborhood State: " + Arrays.deepToString(feasibleNeighborhoodState));

                    // ri
                    int[] mainNodeWorkload = feasibleNeighborhoodState[0];

                    // zi
                    MRFNode.MRFNodeState state = mainNode.calculateResourceStateThatServesWorkload(mainNodeWorkload);
//                    System.out.println("Main Node Workload (ri): " + Arrays.toString(mainNodeWorkload));

                    // calculate V1 & V2
                    double v1 = Integer.MAX_VALUE;
                    double v2 = Integer.MAX_VALUE;
                    if (state != null) {
                        // sig (ri)
                        double[] sigMainNodeWorkload = new double[mainNodeWorkload.length];
                        for (int s = 0; s < mainNodeWorkload.length; s++)
                            sigMainNodeWorkload[s] = revSigmoid(mainNodeWorkload[s], X0_PERC * state.getWorkload()[s]);
//                        System.out.println("Reverse Sigmoid of Main Node Workload (sig(ri)): " + Arrays.toString(sigMainNodeWorkload));

//                        System.out.println("Selected Main Node Resource State: " + Arrays.toString(state.getResources()) + ", Max Workload: "
//                                + Arrays.toString(state.getWorkload()) + ", Power Consumption: " + state.getPowerConsumption());

//                        System.out.println("Calculating V1...");
                        v1 = A * state.getPowerConsumption() * (1 + Arrays.stream(sigMainNodeWorkload).sum());
//                        System.out.println("Calculating V2...");
                        v2 = 0;
                        for (int n = 1; n <= mainNode.neighbors.size(); n++) {
//                            System.out.println("Checking Neighbor Node with id: " + mainNode.neighbors.get(n - 1).id);

                            // rj
                            int[] neighborNodeWorkload = feasibleNeighborhoodState[n];
//                            System.out.println("Neighbor Node Workload (rj): " + Arrays.toString(neighborNodeWorkload));

                            // zj
                            MRFNode.MRFNodeState neighborState
                                    = mainNode.neighbors.get(n - 1).calculateResourceStateThatServesWorkload(neighborNodeWorkload);

                            // condition
                            if (neighborState != null) {
                                // sig (rj)
                                double[] sigNeighborNodeWorkload = new double[neighborNodeWorkload.length];
                                for (int s = 0; s < neighborNodeWorkload.length; s++)
                                    sigNeighborNodeWorkload[s] =
                                            revSigmoid(neighborNodeWorkload[s], X0_PERC * neighborState.getWorkload()[s]);
//                                System.out.println("Reverse Sigmoid of Neighbor Node Workload (sig(ri)): "
//                                        + Arrays.toString(sigNeighborNodeWorkload));

//                                System.out.println("Selected Neighbor Node Resource State: " +
//                                        Arrays.toString(neighborState.getResources()) + ", Max Workload: "
//                                        + Arrays.toString(neighborState.getWorkload()) + ", Power Consumption: "
//                                        + neighborState.getPowerConsumption());
                                v2 += B * dotProduct(mainNodeWorkload, neighborNodeWorkload)
                                        + A * neighborState.getPowerConsumption() * (1 + Arrays.stream(sigNeighborNodeWorkload).sum());
                            } else {
//                                System.out.println("No matching Neighbor Node Resource State found!");
                                v2 = Integer.MAX_VALUE;
                                break;
                            }
                        }
                    } else {
//                        System.out.println("No matching Main Node Resource State found!");
                    }
//                    System.out.println("V1: " + v1);
//                    System.out.println("V2: " + v2);
//                    System.out.println("Vc: " + (v1 + v2));
                    neighborhoodStatesVc[i] = v1 + v2;
                    // calculate Vc
                    neighborhoodStatesTp[i] = Math.pow(Math.E, -1 * (v1 + v2) / temperature(sweep + 1));
//                    System.out.println("Temperature: " + temperature(sweep + 1));
//                    System.out.println("Tp(i): " + neighborhoodStatesTp[i]);
//                    System.out.println("Tp[array]: " + Arrays.toString(neighborhoodStatesTp));
//                    System.out.println("------------------------------------------------------------------------\n");
                    i++;
                }
//                System.out.println("------------------------------------------------------------------------\n");
//                System.out.println("------------------------------------------------------------------------\n");
                // Caclulcate temperature sum
//                System.out.println("Tp[array]: " + Arrays.toString(neighborhoodStatesTp));
//                System.out.println("Vc[array]: " + Arrays.toString(neighborhoodStatesVc));
                // Calculate minimun index of Vc
                int index = 0;
                double minimum = neighborhoodStatesVc[0];
                for (i = 0; i < neighborhoodStatesVc.length; i++) {
                    if (neighborhoodStatesVc[i] < minimum) {
                        index = i;
                        minimum = neighborhoodStatesVc[i];
                    }
                }
//                System.out.println("Vc[array] minimum: " + minimum);
//                System.out.println("Corresponding state: " + Arrays.deepToString(feasibleNeighborhoodStates.get(index)));
                double ts = Arrays.stream(neighborhoodStatesTp).sum();
//                System.out.println("Ts (sum): " + ts);
                // Calculate state probabilities
                for (int s = 0; s < neighborhoodStatesTp.length; s++)
                    neighborhoodStatesP[s] = neighborhoodStatesTp[s] / ts;
//                System.out.println("Pi for each value of ri: " + Arrays.toString(neighborhoodStatesP));
                // Create state cumulative probabilities
                neighborhoodStatesCP[0] = neighborhoodStatesP[0];
                for (int s = 1; s < neighborhoodStatesP.length; s++)
                    neighborhoodStatesCP[s] = neighborhoodStatesCP[s - 1] + neighborhoodStatesP[s];
//                System.out.println("Cumulative Pi for each value of ri: " + Arrays.toString(neighborhoodStatesCP));
                // Select a new state for the neighborhood
                int nextStateIdx = 0;
                Random rand = new Random();
//                double p = rand.nextInt(neighborhoodStatesCP.length) / (1.0 * neighborhoodStatesCP.length);
                double p = rand.nextDouble();
//                System.out.println("Random Seed: " + p);
                for (int s = 0; s < neighborhoodStatesCP.length; s++) {
                    if (p <= neighborhoodStatesCP[s]) {
                        nextStateIdx = s;
                        break;
                    }
                }
//                System.out.println("Next State Index: " + nextStateIdx);
                // Update neighborhood states
                int[][] feasibleNeighborhoodState = feasibleNeighborhoodStates.get(nextStateIdx);
//                System.out.println("...which corresponds to neighborhood state: " +
//                        Arrays.deepToString(feasibleNeighborhoodState));
//                System.out.println("Vc: " + neighborhoodStatesVc[nextStateIdx]);
//                System.out.println("Vm Formation Workload: " + Arrays.toString(mainNode.currentResourcesState.getWorkload()));
                mainNode.updateState(feasibleNeighborhoodState[0]);
                for (int n = 1; n <= mainNode.neighbors.size(); n++) {
                    MRFNode neighbor = mainNode.neighbors.get(n - 1);
                    neighbor.updateState(feasibleNeighborhoodState[n]);
                }

                // Update Vc array:
//                totalVc[mainNode.id] = neighborhoodStatesVc[nextStateIdx];
//                promptEnterKey();
            }
            System.out.println("Current Workload balance: " + "\n");
            for (int i = 0; i < nodes.length; i++) {
                System.out.print(String.format("%8s", Arrays.toString(nodes[i].currentWorkload)));
                if (i != 0 && (i + 1) % gridSize == 0)
                    System.out.println();
                else
                    System.out.print(" | ");
            }
            System.out.println("--------------------------------------");
            System.out.println("\nCurrent Energy Consumption: " + "\n");
            for (int i = 0; i < nodes.length; i++) {
                System.out.print(String.format("%5s",nodes[i].currentResourcesState.getPowerConsumption()));
                if (i != 0 && (i + 1) % gridSize == 0)
                    System.out.println();
                else
                    System.out.print(" | ");
            }
            System.out.println("--------------------------------------");
            // Recalculate total Vcs array
            totalVc = recalculateTotalVc();
            System.out.println("State Vcs: " + Arrays.toString(totalVc));
            System.out.println("Vc Sum: " + Arrays.stream(totalVc).sum());
            updateVcCSVs(sweep, Arrays.stream(totalVc).sum());
        }
    }

    private static double[] recalculateTotalVc() {
        double[] totalVc = new double[nodes.length];

        for (MRFNode mainNode: nodes) {
//            System.out.println("Main Node: " + mainNode.id);
//            System.out.println("Neighbors: " + mainNode.neighbors.size());
            int[] mainNodeWorkload = mainNode.currentWorkload;
            MRFNode.MRFNodeState state = mainNode.currentResourcesState;
            double[] sigMainNodeWorkload = new double[mainNodeWorkload.length];
            for (int s = 0; s < mainNodeWorkload.length; s++)
                sigMainNodeWorkload[s] = revSigmoid(mainNodeWorkload[s], X0_PERC * state.getWorkload()[s]);
            double v1 = A * state.getPowerConsumption() * (1 + Arrays.stream(sigMainNodeWorkload).sum());
            int v2 = 0;
            for (int n = 1; n <= mainNode.neighbors.size(); n++) {
//                System.out.println("Checking Neighbor: " + mainNode.neighbors.get(n - 1).id);
                int[] neighborNodeWorkload = mainNode.neighbors.get(n - 1).currentWorkload;
                MRFNode.MRFNodeState neighborState = mainNode.neighbors.get(n - 1).currentResourcesState;
                double[] sigNeighborNodeWorkload = new double[neighborNodeWorkload.length];
                for (int s = 0; s < neighborNodeWorkload.length; s++)
                    sigNeighborNodeWorkload[s] =
                            revSigmoid(neighborNodeWorkload[s], X0_PERC * neighborState.getWorkload()[s]);
                v2 += B * dotProduct(mainNodeWorkload, neighborNodeWorkload)
                        + A * neighborState.getPowerConsumption() * (1 + Arrays.stream(sigNeighborNodeWorkload).sum());
            }
            double vc = v1 + v2;
            totalVc[mainNode.id] = vc;
        }

        return totalVc;
    }

    private static void updateVcCSVs(int sweepNo, double Vc) {
        // MRF Evaluation
        // Initiate files if not present
        String fileName = "(" + timeStamp + ")MRF.csv";
        File csvFile = new File(System.getProperty("user.dir") + "/evaluation_results/MRF/" + fileName);
        StringBuilder sb = new StringBuilder();
        if(!csvFile.isFile()) {
            sb.append("Sweep, Vc\n");
        }
        try {
            BufferedWriter br = new BufferedWriter(new FileWriter(csvFile, true));

            sb.append(sweepNo + "," + String.format("%.2f", Vc) + "\n");

            br.write(sb.toString());
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static MRFNode[] createMRFGrid(int gridSIze) {
        MRFNode[] nodes = new MRFNode[gridSIze * gridSIze];
        int id = 0;

        for (int i = 0; i < gridSIze; i++) {
            for (int j = 0; j < gridSIze; j++) {
                nodes[id] = new MRFNode(id, new Coordinates(i,j), residualResources[id], residualWorkload[id]);
                id++;
            }
        }
        createNeighborhoods(nodes, hops);
        return nodes;
    }

    private static void createNeighborhoods(MRFNode[] nodes, int hops) {
        for (int i = 0; i < nodes.length; i++) {
            MRFNode mainNode = nodes[i];
            for (int j = 0; j < nodes.length; j++) {
                if (i == j) continue;
                MRFNode candidateNeighbor = nodes[j];
                // Neighboring condition
                int xDist = Math.abs(candidateNeighbor.pos.x - mainNode.pos.x);
                int yDist = Math.abs(candidateNeighbor.pos.y - mainNode.pos.y);
                if ((xDist <= hops) && (yDist <= hops) && (xDist != yDist)) {
                    mainNode.addNeighbor(candidateNeighbor);
//                    System.out.println("Node with id: " + mainNode.id + " and position: " + mainNode.pos.x + "," +
//                            mainNode.pos.y + " has neighbor: " + candidateNeighbor.id);
                }
            }
            // Check if neighborhood has reached max state
            if (mainNode.neighbors.size() > MAX_NEIGHBORHOOD_SIZE - 1) {
//                System.out.println("Neighborhood is too big, randomly removing some neighbors");
                Random rand = new Random();
                // If so, randomly remove neighbors until neighborhood has the desired size
                while (mainNode.neighbors.size() > MAX_NEIGHBORHOOD_SIZE - 1) {
                    int index = rand.nextInt(mainNode.neighbors.size());
                    mainNode.neighbors.remove(index);
                }
            }
        }
    }

    private static double revSigmoid(double x, double x0) {
//        System.out.println("X0 = " + x0);
        return (L - (L / (1 + Math.pow(Math.E, (-1 * k * (x - x0))))));
    }

    private static double dotProduct(int[] vectA, int[] vectB) {
        double product = 0;
        int n = vectA.length;
        // Loop to calculate dot product
        for (int i = 0; i < n; i++)
            product += vectA[i] * vectB[i];
        return product;
    }

    private static double temperature(int w) {
        return (C0 / Math.log(1 + w));
    }

    public static void promptEnterKey(){
        System.out.println("Press \"ENTER\" to continue...");
        Scanner scanner = new Scanner(System.in);
        scanner.nextLine();
    }

    public static void main(String[] args) {
        MRFNode.apps = apps;
        MRFNode.formationsPowerConsumption = powerConsumption;
        MRFNode.formationsWorkload = formationsWorkload;
        MRFNode.relaxationFactor = RELAXATION_FACTOR;

        timeStamp = String.valueOf(new Date());

        nodes = createMRFGrid(gridSize);
        balanceLoadBetweenPois(residualWorkload, residualResources);
    }
}
